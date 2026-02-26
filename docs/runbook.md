# Runbook

## 1. この文書の責務

扱う内容:
- 初動の確認順序
- 症状別の切り分けルール
- 最小復旧の原則
- 復旧判定と事後記録

扱わない内容:
- 障害モードの網羅説明（`docs/failure-modes.md`）
- SLO 閾値の定義（`docs/slo.md`）
- システム構成の詳細説明（`docs/architecture.md`）

## 2. 対象範囲

- Gateway-BFF（OIDC login / `/v1/me` / `/v1/users/**`）
- Account（`/identities:resolve` / `/users/**` / `/admin/**`）
- Entitlement（API + Outbox Relay）
- Matchmaking（Queue API + worker）
- Notification（subscriber + worker + DLQ）
- 依存: Keycloak / PostgreSQL / Redis / NATS JetStream

## 3. インシデント共通ルール

### 3.1 優先順位
1. ユーザー影響の最小化（可用性回復）
2. データ整合性保護（誤更新・二重処理の防止）
3. 根本原因分析に必要な証跡確保

### 3.2 変更ルール
- 1 回の対応で複数要因を同時変更しない
- 先に「戻しやすい操作」（再起動、並列度調整、ルーティング切替）を行う
- スキーマ変更や大量データ修正は一次復旧後に分離して実施する

## 4. 初動フロー（全障害共通）

1. 検知確認
- アラート、問い合わせ、SLO 逸脱のどれで検知したかを記録

2. 影響面の特定
- ログイン導線、認証済み API、通知配信のどこに影響があるかを分類

3. 詰まり箇所の特定
- OIDC（Keycloak） / BFF / Account / Matchmaking / Redis / DB / Outbox / NATS / Notification の順で確認

4. 最小復旧
- 再起動、負荷抑制、接続性回復を優先

5. 復旧判定
- `docs/slo.md` の対象 SLI が回復傾向か確認

6. 記録
- タイムライン、実施操作、未解決リスクを記録

## 5. 症状別ルール

### 5.1 ログインできない（`/login?error` 増加）

対象障害:
- `FM-AUTH-01`
- `FM-AUTH-02`
- `FM-AUTH-03`

確認順序:
1. BFF ログの `oauth2 login failed` 発生有無
2. Keycloak ヘルスと到達性
3. OIDC 設定（issuer, client, redirect URI）
4. セッション Cookie（JSESSIONID）伝播

初期対応:
- Keycloak 不達時は接続経路（Ingress/DNS/Service）を復旧
- 設定不整合時は直前変更をロールバック
- Cookie 問題はプロキシ/ドメイン属性を是正

復旧判定:
- `/login` の 302 応答率が平常化
- `/v1/me` の 401 比率が平常に戻る

### 5.2 `/v1/me` や `/v1/users/**` が 502/504 増加

対象障害:
- `FM-BFF-ACC-03`
- `FM-BFF-ACC-04`
- `FM-DB-01`

確認順序:
1. BFF 側 `ACCOUNT_TIMEOUT` / `ACCOUNT_BAD_GATEWAY` の増加
2. account 側 5xx と DB 遅延の有無
3. account へのネットワーク到達性
4. デプロイ直後なら API 契約差分の有無

初期対応:
- account の pod/プロセス健全性を回復
- DB 高負荷時はワーカー並列度を下げる
- 契約不整合時は互換バージョンへ戻す

復旧判定:
- BFF の 502/504 が継続的に低下
- account の 5xx と p95 が回復傾向

### 5.3 `/v1/users/**` で 401/403 が急増

対象障害:
- `FM-BFF-ACC-01`
- `FM-BFF-ACC-02`
- `FM-ACC-02`
- `FM-ACC-04`

確認順序:
1. `ACCOUNT_INTERNAL_API_TOKEN` と account 側 token の一致
2. `X-User-Id` / `X-User-Roles` の転送有無
3. account 側所有者判定の拒否理由

初期対応:
- ヘッダー名・値の設定を正に戻す
- 秘密情報注入ミス（空文字）を修正
- 権限ヘッダー変換の直前変更をロールバック

復旧判定:
- users API の `401/403` 比率が平常値へ収束

### 5.4 `/v1/me` で `ACCOUNT_INACTIVE` が急増

対象障害:
- `FM-BFF-ACC-05`
- `FM-ACC-03`

確認順序:
1. 直近の suspend 実行件数
2. `audit_logs` 記録の整合
3. users.status 更新件数との突合

確認SQL:
```sql
SELECT status, COUNT(*)
FROM account.users
GROUP BY status
ORDER BY status;
```

```sql
SELECT action, COUNT(*)
FROM account.audit_logs
WHERE created_at >= NOW() - INTERVAL '1 day'
GROUP BY action
ORDER BY action;
```

初期対応:
- 誤停止が疑われる場合は対象を限定してデータ修正
- 監査ログ欠損時は操作ログと突合して影響範囲を確定

復旧判定:
- `ACCOUNT_INACTIVE` 比率が想定範囲に戻る
- 監査記録と実データの整合が取れている

### 5.5 `POST /identities:resolve` の遅延・失敗増

対象障害:
- `FM-ACC-01`
- `FM-DB-01`

確認順序:
1. resolve API の p95 と 5xx
2. unique violation 増加有無
3. DB ロック待ち・接続枯渇

確認SQL:
```sql
SELECT provider, COUNT(*)
FROM account.identities
GROUP BY provider
ORDER BY COUNT(*) DESC;
```

```sql
SELECT COUNT(*)
FROM account.users
WHERE created_at >= NOW() - INTERVAL '10 minutes';
```

初期対応:
- 高負荷時は入口トラフィックを平準化
- DB ボトルネック（ロック/遅延クエリ）を先に解消

復旧判定:
- resolve API の p95 が閾値近傍へ回復
- 5xx の発生率が安定低下

### 5.6 通知が遅延/停止する

対象障害:
- `FM-OB-01`
- `FM-NATS-01`
- `FM-NOTI-03`

確認順序:
1. outbox backlog と publish 遅延
2. NATS consumer lag
3. Notification backlog（`PENDING` / `PROCESSING`）
4. DB ロック待ち/接続枯渇

確認SQL:
```sql
SELECT status, COUNT(*)
FROM notification.notifications
GROUP BY status
ORDER BY status;
```

```sql
SELECT COUNT(*)
FROM notification.notifications
WHERE created_at < NOW() - INTERVAL '10 minutes'
  AND status IN ('PENDING', 'PROCESSING');
```

初期対応:
- worker 停止時は再起動
- DB 高負荷時は worker 並列度を下げる
- NATS 再接続ループ時は認証/ネットワークを確認

復旧判定:
- backlog が単調減少に転じる
- publish/consume 遅延が回復傾向

### 5.7 DLQ / FAILED が増える

対象障害:
- `FM-NOTI-04`
- `FM-NOTI-05`
- `FM-NATS-02`

確認順序:
1. 恒久失敗（payload 不正、必須欠落）か一時失敗かを分類
2. DLQ と通知状態の整合
3. 同一イベント再発の有無

初期対応:
- 恒久失敗は生成元修正を優先
- 一時失敗は backoff/レート制御を調整
- 再投入は事前合意ルールに従い限定実行

復旧判定:
- DLQ の増加が停止
- FAILED 比率が収束

### 5.8 Matchmaking の time-to-match が悪化する

対象障害:
- `FM-MM-01`
- `FM-MM-02`
- `FM-MM-03`

確認順序:
1. `mm.time_to_match` の p95 上昇と `mm.queue.depth{mode}`/`mm.queue.oldest_age{mode}` の継続増加を確認
2. `mm.dependency.error.total{type}` の内訳（`worker_loop`, `time_to_match_*` など）を確認
3. Matchmaking worker ログの mode 別例外と NATS publish 失敗を確認
4. Redis 到達性・遅延と NATS stream/subject 設定を確認

初期対応:
- worker 停滞時はプロセス再起動と worker 間隔/バッチ設定の保守的調整を行う
- Redis 遅延時は負荷を抑制し、Redis 側の資源逼迫を先に解消する
- publish 失敗時は NATS 接続設定を復旧し、影響チケットの通知欠損有無を確認する

復旧判定:
- `mm.time_to_match` の p95 が閾値近傍へ戻る
- `mm.queue.depth` と `mm.queue.oldest_age` が単調減少へ転じる

## 6. 復旧定型手順

### 6.1 最小復旧（共通）
1. 対象コンポーネントの例外頻度を確認
2. Pod/プロセス再起動
3. 主要 SLI（成功率、p95、backlog）の改善を確認

### 6.2 負荷抑制
- Notification worker の並列度を下げる
- Outbox Relay の publish レートを下げる
- account 高負荷時は BFF 側呼び出し負荷を抑制する

### 6.3 整合性チェック
```sql
SELECT COUNT(*)
FROM notification.notifications
WHERE status = 'SENT'
  AND (locked_by IS NOT NULL OR next_retry_at IS NOT NULL);
```

```sql
SELECT COUNT(*)
FROM account.identities i
LEFT JOIN account.users u ON u.user_id = i.user_id
WHERE u.user_id IS NULL;
```

## 7. 復旧完了判定

以下をすべて満たしたら一次復旧完了とする:
- 影響 API の失敗率が低下し続けている
- backlog が減少傾向にある
- 主要 SLI が回復傾向である（`docs/slo.md` 準拠）
- 新たな副作用（権限誤判定、整合性崩れ）が観測されない

## 8. 事後対応（ポストモーテム）

記録必須項目:
1. 影響した SLO/SLI と期間
2. 起点障害モード ID（`docs/failure-modes.md`）
3. 検知から復旧までのタイムライン（TTD/TTR）
4. 実施した暫定対応と恒久対策
5. 再発防止アクションの担当者と期限

## 9. 参照関係

- SLO 閾値と判定方法: `docs/slo.md`
- 障害モード分類: `docs/failure-modes.md`
- 構成とデータフロー: `docs/architecture.md`
