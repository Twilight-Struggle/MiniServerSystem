# E2E Test Coverage

## 1. 目的とスコープ

この文書は、`script/e2e/run.sh` から実行される E2E シナリオ全体を俯瞰し、
「本番運用を想定したときに最低限守るべき振る舞い」を検査項目として定義する。

対象は以下:
- Gateway / Keycloak / Entitlement / Notification / NATS / Postgres を跨ぐ統合挙動
- 正常系だけでなく、再試行・冪等性・DLQ などの障害系挙動
- OIDC 認証後のユーザー同一性・認可境界

## 2. E2E 検査項目一覧

### E2E-01: 基盤サービスの起動健全性

目的:
- 後続シナリオ実行前に、依存サービスが疎通可能な状態であることを確認する。

主な確認内容:
- `GET /actuator/health/readiness` (Gateway) が一定時間内に成功する。
- Keycloak の well-known endpoint が一定時間内に成功する。
- `GET /actuator/health/liveness` (Gateway) が成功する。

担保したいリスク:
- アプリのビジネス検証以前に、起動未完了や依存未接続で誤検知することを防ぐ。

### E2E-02: Entitlement 付与から Notification 反映までのパイプライン整合

目的:
- Entitlement API の付与要求が、Outbox -> NATS -> Notification 永続化まで到達することを確認する。

主な確認内容:
- `POST /v1/entitlements/grants` が `200` を返す。
- `notification.notifications` に対象ユーザーのレコードが 1 件作成される。
- 同イベントの `event_id` が `notification.processed_events` に記録される。

担保したいリスク:
- 付与 API 成功後に非同期連携のどこかでイベントが欠落する障害。
- 永続化と処理済み記録の不整合。

### E2E-03: 再配送時の冪等性（重複イベント耐性）

目的:
- 同一 `event_id` が再配送されても、通知が重複生成されないことを確認する。

主な確認内容:
- NATS Stream から対象イベント payload を取得し、同一内容を再 publish する。
- 再 publish 後も `notification.notifications` 件数が増えない (1 件のまま)。
- `notification.processed_events` も同一 `event_id` で 1 件のまま。

担保したいリスク:
- ネットワーク再送や consumer 再処理での二重通知。
- exactly-once 的な期待に反する重複副作用。

### E2E-04: Notification 送信失敗時のリトライ上限と DLQ 隔離

目的:
- 一時障害が継続した場合に、設定されたリトライ上限で停止し DLQ に隔離されることを確認する。

主な確認内容:
- Notification 送信失敗注入を有効化して対象ユーザーのみ失敗させる。
- `POST /v1/entitlements/grants` 実行後、対象通知が最終的に `status=FAILED` になる。
- `attempt_count` が `maxAttempts` と一致する。
- `notification.notification_dlq` に同イベントが 1 件登録される。
- テスト終了時に失敗注入関連の環境変数をデフォルトへ復元する。

担保したいリスク:
- 失敗時に無限再試行して回復不能になる障害。
- FAILED 状態と DLQ 登録の不整合。

### E2E-05: OIDC ログイン成立と本人データ参照の同一性

目的:
- OIDC ログイン後に、BFF 経由で取得する `myUserId` と `/v1/users/{id}` の対象が一致することを確認する。

主な確認内容:
- `/login` から Keycloak 認証フローを完了できる。
- `GET /v1/me` で `userId` を取得できる。
- `GET /v1/users/{myUserId}` が `200` を返し、レスポンス `userId` が一致する。

担保したいリスク:
- 認証済み主体と downstream 参照 ID の取り違え。
- BFF -> Account 連携での subject 伝播不整合。

### E2E-06: 他ユーザー情報への水平アクセス拒否

目的:
- 認証済みユーザーが他人のユーザーIDを指定しても情報取得できないことを確認する。

主な確認内容:
- OIDC ログイン後、`GET /v1/users/{otherUserId}` を実行する。
- 応答が `403` または `404` であることを確認する。

担保したいリスク:
- 水平権限昇格。

## 3. この E2E セットで保証する品質特性

- 可用性前提: テスト開始時点で依存サービスが ready であること。
- 連携整合性: 同期 API 成功後に非同期処理まで到達すること。
- 冪等性: 同一イベント再投入で副作用を重複させないこと。
- 障害収束性: リトライ上限到達時に FAILED/DLQ へ収束すること。
- 認証/認可境界: 本人性を維持し、他者アクセスを拒否すること。

## 4. 既知の範囲外（この E2E だけでは未保証）

- 大量トラフィック時の性能・遅延特性。
- 複数同時障害時の復旧時間や運用手順妥当性。
- 外部通知先の実システム連携品質（本テストでは失敗注入を利用）。

必要に応じて、負荷試験・障害注入試験・運用Runbook検証を別テストとして補完する。
