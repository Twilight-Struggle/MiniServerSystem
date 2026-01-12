# EntitlementとNotificationの最初の実装
## 目標
- 権利(Entitlement)の正本を RDB に持つ
- API で権利を付与/剥奪できる(同期的)
- 付与/剥奪の結果を 非同期イベントとして発行し、Notification 側で処理する
- イベント配信は at-least-once 前提で、冪等性確保も実装する
- Outbox パターンで「DB更新とイベント発行の整合性」を担保する

## データフロー
1. APIでEntitilement appで付与は剥奪
2. RDBの同一トランザクションでentitlements更新+outbox_events追加
3. Entitilement appがoutbox_eventsを読みNATSにpublishし、outbox_eventsを送信済みに変更
4. Notification appがNATSを冪等性確保しつつsubscribe(通知はまだ行わない)
5. 失敗時リトライ、完全失敗時はDLQ

## Entitlement app
### 入口のAPI
#### 権利付与
- POST /v1/entitlements/grants
- Header: Idempotency-Key: <uuid or unique string> ← purchase_idで良い
- Body
```json
{
  "user_id": "u_123",
  "stock_keeping_unit": "item1",
  "reason": "purchase",
  "purchase_id": "p_456"
}
```
- response
```json
{
  "user_id": "u_123",
  "stock_keeping_unit": "item1",
  "status": "ACTIVE",
  "version": 12,
  "updated_at": "2026-01-08T07:10:00Z"
}
```

#### 権利剥奪
- POST /v1/entitlements/revokes
- Header: Idempotency-Key
- Body
```json
{
  "user_id": "u_123",
  "stock_keeping_unit": "item1",
  "reason": "refund",
  "purchase_id": "p_457"
}
```
- response
```json
{
  "user_id": "u_123",
  "stock_keeping_unit": "item1",
  "status": "REVOKED",
  "version": 13,
  "updated_at": "2026-01-08T07:11:00Z"
}
```

#### 参照
GET /v1/users/{user_id}/entitlements
- response
```json
{
  "user_id": "u_123",
  "entitlements": [
    {
      "stock_keeping_unit": "item1",
      "status": "ACTIVE",
      "version": 12,
      "updated_at": "2026-01-08T07:10:00Z"
    }
  ]
}
```

#### エラー応答
同じ HTTP ステータスでも原因を区別するため、エラーコードを返す。

```json
{
  "code": "ENTITLEMENT_STATE_CONFLICT",
  "message": "already ACTIVE"
}
```

- `IDEMPOTENCY_KEY_CONFLICT`: 同一 Idempotency-Key で異なるリクエスト
- `ENTITLEMENT_STATE_CONFLICT`: 既に同じ状態（ACTIVE/REVOKED）で更新不可
- `BAD_REQUEST`: 入力不正
  - 入力検証は jakarta.validation / 標準例外で行い、ApiExceptionHandler で ApiErrorResponse に整形する。

#### idempotency_keys
APIの再送ではこのテーブルに保存された結果を用いて同一の結果を返す。
同一キーで異なるリクエストが来た場合は409。
TTLは24h。
失敗応答（エラーコードとメッセージ）も保存し、再送時に同じエラーを返す。
同一Idempotency-Keyの同時実行はトランザクション内の 64-bit Advisory Lock（pg_advisory_xact_lock(bigint)）で直列化し、
ロックキーは Idempotency-Key を SHA-256 でハッシュし、先頭 8byte を long に変換して生成する。
競合判定はロック取得後の未期限切れ `idempotency_keys` の確認に集約し、保存段階では競合を扱わない。
保存は「期限切れのみ更新するUPSERT」とし、未期限切れで更新されない場合は
先頭の競合判定をすり抜けた不変条件違反として内部エラー扱いとする。

#### retention cleanup
idempotency_keys は expires_at <= now() を削除する。
outbox_events は status=PUBLISHED かつ published_at が TTL(24h) を過ぎたものだけ削除する。
削除ワーカーは 1h 間隔で実行する。

#### entitlement_audit
操作履歴を保存しておく。でかくなるのを防ぐにはdetailを小さくし、月次パーティショニングなどを行う。

### NATSへのpublish
- status=PENDING かつ next_retry_at <= now() を一定件数取得
- Broker に publish(NATS headerに event_id を入れる)
- 成功したら PUBLISHED、失敗したらアプリ側で次回の attempt_count を計算し、next_retry_at も指数バックオフで計算して更新
- attempt_count が閾値超えたら FAILED(運用介入)
- ペイロード解析失敗はリトライで回復しないため即時 FAILED とし、アラート対象にする

#### 複数Podがpublishする運用
claim(ロック)→ publish → finalize の3段階にする
```sql
WITH cte AS (
  SELECT event_id
  FROM outbox_events
  WHERE
    (
      status = 'PENDING'
      AND (next_retry_at IS NULL OR next_retry_at <= :now)
    )
    OR
    (
      status = 'IN_FLIGHT'
      AND (lease_until IS NULL OR lease_until <= :now)          -- リース切れ回収
    )
  ORDER BY created_at
  LIMIT $1
  FOR UPDATE SKIP LOCKED
)
UPDATE outbox_events e
SET
  status = 'IN_FLIGHT',
  locked_by = $2,                       -- worker_id / pod名
  locked_at = now(),
  lease_until = now() + interval '30 seconds',
  last_error = NULL
FROM cte
WHERE e.event_id = cte.event_id
RETURNING e.event_id, e.event_type, e.payload, e.attempt_count;
```
→NATSへpublish(message key：event_id, headers：event_type, aggregate_key, occurred_at, trace_id)
→
```sql
UPDATE outbox_events
SET
  status = 'PUBLISHED',
  published_at = now(),
  lease_until = NULL
WHERE event_id = $1 AND locked_by = $2;

```
→失敗時（attempt_count/status/next_retry_at はアプリ側で計算した値を渡す）
```sql
UPDATE outbox_events
SET
  attempt_count = $attemptCount,
  status = $status,
  next_retry_at = $nextRetryAt,
  lease_until = NULL,
  last_error = $err
WHERE event_id = $1 AND locked_by = $2;
```
バックオフは指数バックオフ+ジッター

base=1s, cap=60s、delay = min(cap, base * 2^(attempt - 1)) * (0.5 + rand())
設定キー: `entitlement.outbox.backoff-base` / `entitlement.outbox.backoff-max` / `entitlement.outbox.backoff-min` / `entitlement.outbox.backoff-jitter-min` / `entitlement.outbox.backoff-jitter-max`（Duration は `1s` 形式）

#### NATSのイベント
protocol bufferを使用
- event_id(UUID、重複排除キー)
- event_type(EntitlementGranted, EntitlementRevoked)
- occurred_at
- user_id
- stock_keeping_unit
- source
- source_id
- version(entitlementsのversion。将来の整合性検証に効く)

#### NATS 設定
- entitlement.nats.subject: 必須 (publish 先 subject)
- entitlement.nats.stream: 必須 (JetStream stream 名)
- entitlement.nats.duplicate-window: Nats-Msg-Id の重複排除窓

#### 運用パラメータ
- poll interval：200ms〜1s(小規模なら1sで十分、設定は `entitlement.outbox.poll-interval`)
- batch size：50〜200(ローカルは50)
- lease：30s(publishが遅い場合は延長、設定は `entitlement.outbox.lease`)
- max_attempt：10(とりあえずは10で十分)

## Notification app
とりあえず通知をDBに保存するところまで
### テスト方針
- Testcontainers の Postgres で統一する(test プロファイル)。
### 動作
- NATSから冪等性を確保(idem, event_id)しつつイベントをsubscribe
- Notification appはJetStreamのdurable consumerで購読し、処理成功時に明示ackする（設定: `notification.nats.stream` / `notification.nats.durable`）
- Notification appは起動時に stream を作成し、Nats-Msg-Id 重複排除のため duplicate-window を指定する
- Notification appは ack-wait と max-deliver を固定値で設定し、再配信猶予と最大リトライ回数を制御する
- advisory 設定: MaxDeliver は `notification.nats.advisory.*`、terminated は `notification.nats.terminated-advisory.*` を使用する
- 受信/デバッグ時のペイロード解析失敗や不正payloadは恒久的失敗として TERM し、JetStream 側で再配信停止を確定させる
- 通知レコードを保存(通知を模擬) SENTまでの状態遷移
- 失敗時リトライ、完全失敗時はDLQ
- DLQは通知単位で一意（notification_id で一意制約）。二重登録は握りつぶす（ON CONFLICT DO NOTHING）
- MaxDeliver advisory を購読し、stream_seq を notification_nats_dlq に保存する
- terminated advisory を購読し、TERM 済みの stream_seq を notification_nats_dlq に保存する
- claimを利用

### 配信のclaim/lease
複数ワーカーで同一通知を処理しないために、PENDING と lease 期限切れの PROCESSING を claim する。
`locked_by` はホスト名を使用し、`HOSTNAME` 環境変数を優先、未設定時は `InetAddress.getLocalHost().getHostName()` にフォールバックする。

```sql
WITH cte AS (
  SELECT notification_id
  FROM notifications
  WHERE (
    status = 'PENDING'
    AND (next_retry_at IS NULL OR next_retry_at <= now())
  )
  OR (
    status = 'PROCESSING'
    AND (lease_until IS NULL OR lease_until <= now())  -- リース切れ回収
  )
  ORDER BY created_at
  LIMIT $1
  FOR UPDATE SKIP LOCKED
)
UPDATE notifications n
SET
  status = 'PROCESSING',
  locked_by = $2,         -- ホスト名
  locked_at = now(),
  lease_until = now() + interval '30 seconds'
FROM cte
WHERE n.notification_id = cte.notification_id
RETURNING n.notification_id, n.event_id, n.payload_json, n.attempt_count;
```

送信成功:
```sql
UPDATE notifications
SET
  status = 'SENT',
  sent_at = now(),
  next_retry_at = NULL,
  locked_by = NULL,
  locked_at = NULL,
  lease_until = NULL
WHERE notification_id = $1 AND locked_by = $2 AND status = 'PROCESSING';
```

失敗時（attempt_count/status/next_retry_at はアプリ側で計算した値を渡す）:
```sql
UPDATE notifications
SET
  attempt_count = $attemptCount,
  status = $status,
  next_retry_at = $nextRetryAt,
  locked_by = NULL,
  locked_at = NULL,
  lease_until = NULL
WHERE notification_id = $1 AND locked_by = $2 AND status = 'PROCESSING';
```

### クリーンアップ方針
- processed_events: processed_at が 30日より古いものを削除する
- processed_events: threshold と同時刻の行は削除せず残す（processed_at < threshold のみ削除）
- notifications: created_at が 30日より古く、status が SENT/FAILED のみ削除する
- PENDING/PROCESSING が 30日より古い場合は異常としてエラーログを出し、削除せずに残す
- 実行間隔: 1h (cleanup-interval=1h)
- 設定キー: notification.retention.enabled / notification.retention.retention-days / notification.retention.cleanup-interval

### デバッグ用API
外部に送信を作らないため、動作確認用のAPIが必要
GET /debug/notification/inbox/{user_id}

### 端折った部分
#### NATSの毒メッセージ
処理不能なメッセージがある場合、MaxDeliverを超える
NATSのMaxDeliverを超えた場合は、MaxDeliver advisory を購読して stream_seq を notification_nats_dlq に保存する
これはDBに障害が発生したときに起きうる
復旧後、DLQからもとのキューに戻すオペレーションを行えば処理が再開される

#### 外部送信の冪等性
外部送信はログのみの実装
実際にはidempotency keyなどで冪等性を確保する必要がある

#### ボリュームのインフラ
クラウド基盤などを使う前提で、ボリューム(DB, NATS)のインフラは簡素なものになっている

## テスト
- `./gradlew test` は Docker が必要（Testcontainers で Postgres を起動）
- Spring のコンテキストキャッシュと整合するよう、テスト用 Postgres は JVM 内で起動したまま共有する
- PostgreSQL JDBC の型推論を避けるため、通知の JDBC 経由の日時は Timestamp へ変換して投入する
- Entitlement のテストは `spring-boot-starter-test` で完結し、追加の WebMVC テスト starter は不要
