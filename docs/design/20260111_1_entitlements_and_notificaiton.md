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
  "purchase_id": "p_456"
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

#### idempotency_keys
APIの再送ではこのテーブルに保存された結果を用いて同一の結果を返す。
同一キーで異なるリクエストが来た場合は409。
TTLは24h。

#### entitlement_audit
操作履歴を保存しておく。でかくなるのを防ぐにはdetailを小さくし、月次パーティショニングなどを行う。

### NATSへのpublish
- status=PENDING かつ next_retry_at <= now() を一定件数取得
- Broker に publish(メッセージキーに event_id を入れる)
- 成功したら PUBLISHED、失敗したら attempt_count++ と next_retry_at を指数バックオフで更新
- attempt_count が閾値超えたら FAILED(運用介入)

#### 複数Podがpublishする運用 ?
claim(ロック)→ publish → finalize の3段階にする
```sql
WITH cte AS (
  SELECT event_id
  FROM outbox_events
  WHERE
    (
      status = 'PENDING'
      AND (next_retry_at IS NULL OR next_retry_at <= now())
    )
    OR
    (
      status = 'IN_FLIGHT'
      AND lease_until <= now()          -- リース切れ回収
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
→失敗時
```sql
UPDATE outbox_events
SET
  attempt_count = attempt_count + 1,
  status = CASE WHEN attempt_count + 1 >= $max_attempt THEN 'FAILED' ELSE 'PENDING' END,
  next_retry_at = CASE WHEN attempt_count + 1 >= $max_attempt
                 THEN NULL
                 ELSE now() + make_interval(secs => $backoff_seconds)
                 END,
  lease_until = NULL,
  last_error = $err
WHERE event_id = $1 AND locked_by = $2;
```
バックオフは指数バックオフ+ジッター

base=1s, cap=60s、delay = min(cap, base * 2^attempt) * (0.5 + rand())

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

#### 運用パラメータ
- poll interval：200ms〜1s(小規模なら1sで十分)
- batch size：50〜200(ローカルは50)
- lease：30s(publishが遅い場合は延長)
- max_attempt：10(とりあえずは10で十分)

## Notification app
とりあえず通知をDBに保存するところまで
### 動作
- NATSから冪等性を確保(idem, event_id)しつつイベントをsubscribe
- 通知レコードを保存(通知を模擬) SENTまでの状態遷移
- 失敗時リトライ、完全失敗時はDLQ
- claimを利用

### デバッグ用API
外部に送信を作らないため、動作確認用のAPIが必要
GET /debug/notification/inbox/{user_id}