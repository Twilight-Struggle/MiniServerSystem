# Failure Modes

- どこで: `docs/failure-modes.md`
- 何を: MiniServerSystem の代表的な障害モードを定義する
- なぜ: 実装済みの主要導線（OIDC ログイン、account 連携、outbox 通知伝播）で「何が壊れるか」「どの SLO に影響するか」を明確化する

## 1. この文書の責務

扱う内容:
- 障害モードの分類
- ユーザー影響/SLO 影響
- 検知シグナル
- 緩和方針

扱わない内容:
- 具体的なオペレーション手順や SQL（`docs/runbook.md`）
- SLO 閾値の定義（`docs/slo.md`）
- システム全体構成（`docs/architecture.md`）

## 2. 読み方

各障害モードは以下で記述する:
- ID
- 症状
- 影響（ユーザー影響と SLO 影響）
- 検知
- 緩和方針

## 3. 障害モード一覧

### 3.1 認証境界（Gateway-BFF と OIDC）

#### FM-AUTH-01: OIDC エンドポイント到達不能（Keycloak 不達）
- 症状: `/login` 後の認可リダイレクトが失敗し、`/login?error` が増加する
- 影響: ログイン導線停止、`Gateway-BFF SLI-B`（ログイン導線可用性）悪化
- 検知: `oauth2 login failed` ログ増加、`/login?error` 比率増、Keycloak ヘルス不調
- 緩和方針: Keycloak 可用性回復を優先し、DNS/Ingress/Realm 設定を切り分ける

#### FM-AUTH-02: OIDC コールバック不整合（state/nonce/issuer 不一致）
- 症状: 認可後にセッション確立できず、ログイン失敗に遷移する
- 影響: 断続的ログイン失敗、`Gateway-BFF SLI-B` 悪化
- 検知: コールバック直後の認証失敗ログ増、特定 IdP 設定変更直後に発生
- 緩和方針: OIDC Provider 設定（issuer, client, redirect URI）を正に戻し、環境差分を削減する

#### FM-AUTH-03: セッション/Cookie 不整合
- 症状: ログイン成功直後に `/v1/me` が `401` を返す
- 影響: 認証済み導線の利用不能、`Gateway-BFF SLI-A` 悪化
- 検知: `/login` 成功率に対し `/v1/me` 401 比率が異常増、JSESSIONID 欠損
- 緩和方針: Cookie 属性、ドメイン、プロキシ配下のヘッダー伝播を確認する

### 3.2 サービス間連携（Gateway-BFF -> Account）

#### FM-BFF-ACC-01: 内部 API トークン不一致
- 症状: account が内部呼び出しを拒否し、BFF 側で `ACCOUNT_UNAUTHORIZED` / `ACCOUNT_FORBIDDEN` が増える
- 影響: `/v1/me`、`/v1/users/**` の失敗増、`Gateway-BFF SLI-A` 悪化
- 検知: `401/403` 応答増、`ACCOUNT_*` エラーコード増、設定変更直後の急増
- 緩和方針: `ACCOUNT_INTERNAL_API_TOKEN` と account 側トークン設定の一致を確認する

#### FM-BFF-ACC-02: 転送ヘッダー欠落（`X-User-Id` / `X-User-Roles`）
- 症状: account の所有者認可で `401/403` が増加し、ユーザー参照/更新が失敗する
- 影響: `/v1/users/**` 失敗率上昇、`Gateway-BFF SLI-A` 悪化
- 検知: users API で `401/403` 偏在、内部リクエストヘッダー欠落ログ
- 緩和方針: BFF 側 header 設定名と account 側受信設定名の整合を確認する

#### FM-BFF-ACC-03: account への接続タイムアウト/通信失敗
- 症状: BFF が `ACCOUNT_TIMEOUT` / `ACCOUNT_BAD_GATEWAY` を返す
- 影響: `/v1/me` と `/v1/users/**` の失敗率増、`Gateway-BFF SLI-A` と `Account SLI` 同時悪化
- 検知: 502/504 増、RestClient timeout 例外増、ネットワークエラー増
- 緩和方針: account 側負荷/DB ボトルネックとネットワーク経路を同時確認し、短期は負荷抑制で保護する

#### FM-BFF-ACC-04: account 応答不正（空ボディ/スキーマ不整合）
- 症状: BFF が `ACCOUNT_INVALID_RESPONSE` を返す
- 影響: 認証済み API 利用不能、`Gateway-BFF SLI-A` 悪化
- 検知: `INVALID_RESPONSE` の急増、デプロイ境界でのみ再現
- 緩和方針: API 契約差分を解消し、後方互換を保つリリース順序に修正する

#### FM-BFF-ACC-05: account 非アクティブ判定急増
- 症状: `/v1/me` が `403 ACCOUNT_INACTIVE` を継続的に返す
- 影響: 特定ユーザー群が利用不能（仕様上の拒否）
- 検知: `ACCOUNT_INACTIVE` 比率増、管理操作（suspend）件数との乖離
- 緩和方針: 誤 suspend/ステータス更新の有無を監査ログで確認し、誤判定時はデータ修正する

### 3.3 Account（同定・認可・監査）

#### FM-ACC-01: 同一 identity の高競合（`provider+subject`）
- 症状: 一意制約違反が増加し、resolve レイテンシが上昇する
- 影響: `/identities:resolve` 遅延、`Account SLI-A/SLI-C` 悪化
- 検知: unique violation ログ増、resolve p95 悪化
- 緩和方針: 競合は再読込で収束させる前提を維持し、スパイク時はアプリ負荷を平準化する

#### FM-ACC-02: 所有者認可の過剰拒否/過剰許可
- 症状: 正当リクエストが拒否される、または admin 権限が意図せず付与される
- 影響: ユーザー操作不能または権限逸脱リスク、`Account SLI-B` 悪化
- 検知: `403` 増加の偏り、`X-User-Roles` 解析結果の不整合
- 緩和方針: 役割ヘッダー変換と `ROLE_ADMIN` 判定を重点監査し、権限境界テストを維持する

#### FM-ACC-03: suspend 時の監査ログ永続化失敗
- 症状: ステータス更新と監査記録の整合が崩れる
- 影響: 追跡不能、運用監査リスク
- 検知: suspend 実行件数と `audit_logs` 件数の乖離
- 緩和方針: 同一トランザクション保証を維持し、障害時は不整合差分を優先修復する

#### FM-ACC-04: 内部 API 設定ミスによる常時未認証
- 症状: `X-Internal-Token` 未設定/空設定で内部 API が常時拒否される
- 影響: BFF 経由機能がほぼ停止、`Gateway-BFF SLI-A` 悪化
- 検知: 内部 API 全面 `401/403`、起動後すぐに再現
- 緩和方針: 秘密情報注入経路（Helm/環境変数）を確認し、空文字注入を防ぐ

### 3.4 データ層（PostgreSQL）

#### FM-DB-01: 接続枯渇 / スロークエリ / ロック競合
- 症状: API 処理と claim/update が遅延し、バックログが増える
- 影響: account/entitlement/notification 横断でタイムアウト増、複数 SLO 悪化
- 検知: DB ロック待ち増、遅延クエリ増、アプリ側 timeout 増
- 緩和方針: ワーカー並列度抑制、遅いクエリ特定、インデックス/クエリ改善

#### FM-DB-02: 冪等テーブル肥大（retention 不備）
- 症状: `processed_events` 肥大で挿入/検索遅延
- 影響: Notification 遅延、backlog 増加
- 検知: テーブルサイズ増、retention 削除件数が継続的に 0
- 緩和方針: retention 設定見直し、定期実行の監視追加

#### FM-DB-03: アプリ間スキーマ差分（マイグレーション不整合）
- 症状: 特定 API のみ 5xx、デプロイ後に突然発生
- 影響: account/entitlement の API 失敗率増、`Account SLI`/`Entitlement SLI` 悪化
- 検知: SQL 例外（列不足/型不一致）増、リリース境界に一致
- 緩和方針: マイグレーション適用順序と互換期間を明確化し、段階的ロールアウトを行う

### 3.5 Outbox Relay（Entitlement -> JetStream）

#### FM-OB-01: publish 停滞（Outbox バックログ増）
- 症状: outbox が PENDING/IN_FLIGHT のまま滞留
- 影響: 通知が生成されない、`Entitlement SLI-C/D` 悪化
- 検知: outbox backlog 増、`attempt_count` 増、publish error 増
- 緩和方針: Relay 健全性確認、NATS 健全性確認、lease/リトライ設定見直し

#### FM-OB-02: payload 恒久不正
- 症状: 解析不能で再試行しても回復しない
- 影響: 個別イベントが配信不可のまま残る
- 検知: 同一イベントの失敗継続、FAILED 増
- 緩和方針: 生成元修正、FAILED 監視の即時通知

#### FM-OB-03: lease 競合による lock lost
- 症状: publish 後更新が 0 件、lock lost ログ
- 影響: 重複処理増加、進行遅延
- 検知: lock lost ログ、更新 0 件の増加
- 緩和方針: lease 時間調整、処理時間短縮、時刻同期確認

### 3.6 NATS JetStream / Subscriber

#### FM-NATS-01: consumer lag 増加
- 症状: stream 側にメッセージ滞留
- 影響: Notification 遅延の連鎖、`Notification SLI-B/C` 悪化
- 検知: consumer lag 増、Notification 処理遅延増
- 緩和方針: consumer スケール、DB 側ボトルネック同時調査

#### FM-NATS-02: 受信 payload 解析失敗
- 症状: decode 例外で処理不可
- 影響: 当該イベント処理失敗、契約不整合リスク
- 検知: 解析失敗件数増
- 緩和方針: 恒久失敗扱いで隔離し、契約破壊の発生源を修正

#### FM-NATS-03: JetStream advisory DLQ 取り込み停滞
- 症状: MaxDeliver/MSG_TERMINATED は発生しているが `notification.notification_nats_dlq` への記録が増えない
- 影響: 再投入対象（stream_seq）の追跡不能化、復旧判断遅延
- 検知: advisory 購読ログと `notification.notification_nats_dlq` 件数の乖離
- 緩和方針: advisory subscriber の稼働、DB 接続、subject/stream/durable 設定を確認する

### 3.7 Notification（永続化 + 配信）

#### FM-NOTI-01: 同一イベント多重処理
- 症状: 同一 `event_id` の重複処理リスク
- 影響: 二重通知リスク
- 検知: 重複抑止ヒット増、重複挿入失敗ログ
- 緩和方針: `processed_events` 一意制約と先行登録を維持

#### FM-NOTI-02: claim 競合による多重配信
- 症状: 同一通知を複数ワーカーが処理
- 影響: 二重送信、状態不整合
- 検知: mark 更新 0 件、lock lost ログ、attempt 異常増
- 緩和方針: claim + lease 設計の維持、`locked_by` 条件厳格化

#### FM-NOTI-03: 外部送信先不調でリトライ増幅
- 症状: `next_retry_at` 先送りと backlog 増加
- 影響: 遅延増、最終的な FAILED/DLQ 増
- 検知: retry 回数増、処理遅延増、DLQ 増
- 緩和方針: backoff 調整、レート制限、送信先切り離し

#### FM-NOTI-04: max attempts 超過で DLQ 増加
- 症状: FAILED/DLQ が増える
- 影響: 配信不能の顕在化、問い合わせ増、`Notification SLI-D` 悪化
- 検知: DLQ 件数増、FAILED 比率増
- 緩和方針: 恒久失敗/一時失敗の分類、再処理フロー整備

#### FM-NOTI-05: DLQ と通知状態の不整合
- 症状: DLQ のみ登録、または通知状態のみ更新
- 影響: 監査不能、再処理判断不能
- 検知: DLQ と notifications の突合差分
- 緩和方針: 同一 Tx 保証、ロック喪失時 rollback 徹底

## 4. 不変条件（Invariant）

- I1: ログイン導線は正常時に `/login -> 302 -> OIDC` を満たす
- I2: `/v1/me` は `accountStatus=ACTIVE` のみ成功させる
- I3: account 内部 API は有効な内部トークンなしで成功しない
- I4: `provider+subject` は最終的に単一 `user_id` へ収束する
- I5: 同一 `event_id` は最終的に 1 度のみ有効処理される
- I6: 通知は `SENT` か `FAILED` に収束する（lease 回収前提）
- I7: DLQ 登録と `FAILED` 更新は原子的に扱う
- I8: ロック喪失時に部分的副作用だけを残さない

## 5. SLO との対応（要点）

- Gateway-BFF SLI-A/B: FM-AUTH-01/02/03, FM-BFF-ACC-01/02/03/04/05
- Account SLI-A/B/C: FM-ACC-01/02/03/04, FM-DB-01/03
- Entitlement SLI-C/D/E: FM-OB-01/02/03, FM-DB-01
- Notification SLI-B/C/D: FM-NATS-01/02/03, FM-NOTI-01/02/03/04/05, FM-DB-02

## 6. 参照関係

- SLO 閾値と集計ルール: `docs/slo.md`
- 構成/データフローの前提: `docs/architecture.md`
- 実際の一次対応手順: `docs/runbook.md`
