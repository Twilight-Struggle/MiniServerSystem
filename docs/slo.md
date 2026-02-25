# SLO (Service Level Objectives)

- どこで: `docs/slo.md`
- 何を: MiniServerSystem の信頼性目標（SLO）と測定指標（SLI）を定義する
- なぜ: 実装済み機能（OIDC ログイン、account 連携、outbox 非同期連携）に対して、ユーザー影響ベースの運用判断を可能にする

## 1. この文書の責務

扱う内容:
- SLO/SLI の定義
- エラーバジェット
- アラート評価方針（即時対応/追跡対応）
- 集計時の母数・除外ルール

扱わない内容:
- システム構成/データフロー（`docs/architecture.md`）
- 障害モードの詳細分類（`docs/failure-modes.md`）
- 復旧オペレーション（`docs/runbook.md`）

## 2. SLO 設計原則

- 原則 1: SLI は「ユーザーが観測する結果」を優先する
- 原則 2: 4xx のうち、仕様上正しい拒否（認可拒否・未認証）は失敗に含めない
- 原則 3: 5xx とタイムアウトは失敗として一律計上する
- 原則 4: 依存先障害を隠さず、BFF 側 SLO にも影響させる
- 原則 5: 28 日ローリングで判定し、短期 burn rate で早期検知する

## 3. 対象サービスと計測ウィンドウ

対象サービス:
- Gateway-BFF
- Account
- Entitlement
- Matchmaking
- Notification

計測ウィンドウ:
- 公式判定: 28 日ローリング
- アラート補助: 5 分 / 60 分（burn rate 監視）

## 4. SLO / SLI 定義

### 4.1 Gateway-BFF

SLI-A: 認証済み API 成功率（`/v1/me`, `/v1/users/**`）
- 定義: `成功応答 / 全リクエスト`
- 成功応答:
- 2xx
- `403 ACCOUNT_INACTIVE`（仕様上の業務拒否）
- 失敗応答:
- 5xx
- `502/504`（account 連携障害）
- タイムアウト
- SLO: 99.9% / 28日

SLI-B: ログイン導線の可用性（`GET /login`）
- 定義: `/login` の正常遷移率
- 成功応答: `302`（OIDC 認可エンドポイントへ遷移）
- 失敗応答: 5xx, タイムアウト
- SLO: 99.95% / 28日

SLI-C: レイテンシ（認証済み API の p95）
- 定義: `/v1/me`, `/v1/users/**` のサーバー処理時間 p95
- SLO: p95 < 400ms / 28日

補足:
- 未認証アクセスに対する `401` は仕様どおりのため失敗に含めない
- `account` 依存障害は `ACCOUNT_BAD_GATEWAY` / `ACCOUNT_TIMEOUT` として失敗計上する

### 4.2 Account

SLI-A: Identity Resolve 成功率（`POST /identities:resolve`）
- 定義: 有効リクエストに対する成功率
- 成功応答: 2xx
- 失敗応答: 5xx, タイムアウト
- SLO: 99.95% / 28日

SLI-B: User API 成功率（`GET/PATCH /users/{userId}`）
- 定義: 認可済みリクエストに対する成功率
- 成功応答: 2xx
- 除外: `401/403/404`（仕様上の拒否または対象なし）
- 失敗応答: 5xx, タイムアウト
- SLO: 99.9% / 28日

SLI-C: レイテンシ（主要 API の p95）
- 定義: `/identities:resolve`, `/users/{userId}` の p95
- SLO: p95 < 250ms / 28日

補足:
- 同一 `provider+subject` 並行要求の競合収束（一意制約違反後の再読込）は成功扱い
- suspend API は管理系操作として別途運用監視し、現時点の公式 SLO 判定対象外

### 4.3 Entitlement

SLI-A: 権利付与/剥奪処理の成功率
- SLO: 99.95% / 28日

SLI-B: 反映遅延（付与完了から照会反映までの p95）
- SLO: p95 < 1s / 28日

SLI-C: Outbox publish 遅延（`created_at -> published_at` の p95）
- SLO: p95 < 5s / 28日

SLI-D: Outbox 滞留（`now - created_at` の p95）
- SLO: p95 < 10s / 28日

SLI-E: Outbox failed 件数
- SLO: 0 件を維持

### 4.4 Matchmaking

SLI-A: キュー参加 API 成功率
- SLO: 99.9% / 28日

SLI-B: Time-to-Match（`queue_joined_at -> matched_at` の p95）
- SLO: p95 < 10s / 28日

### 4.5 Notification

SLI-A: 通知配信成功率（`SENT / (SENT + FAILED)`）
- SLO: 99.9% / 28日

SLI-B: End-to-End 遅延（`event occurred_at -> notification sent_at` の p95）
- SLO: p95 < 30s / 28日

SLI-C: Backlog（`PENDING` + lease 切れ `PROCESSING`）
- SLO: 平常時ゼロ近傍、異常時は単調増加を継続させない

SLI-D: DLQ 発生件数
- SLO: 0 件を維持

## 5. エラーバジェット

- 99.9%: 28日で約 43 分
- 99.95%: 28日で約 21 分

運用ルール:
- バジェット急消費時は新規変更を抑制し、復旧と再発防止を優先する
- バジェット消費の根拠はインシデント記録に残す
- 同一根本原因で複数 SLO が同時悪化した場合、原因サービスの改善を優先する

## 6. アラート方針

### 6.1 即時対応（ページ対象）
- Gateway-BFF 認証済み API 成功率の急低下（5 分窓）
- Gateway-BFF で `ACCOUNT_BAD_GATEWAY` / `ACCOUNT_TIMEOUT` が連続増加
- Account `POST /identities:resolve` の 5xx 率急増
- Notification DLQ 発生
- Notification backlog の継続増加

### 6.2 追跡対応（営業時間内）
- 60 分窓で SLO 未達傾向
- Entitlement outbox 遅延の継続
- Matchmaking time-to-match 劣化の継続
- OIDC ログイン失敗率の継続増加（`/login?error` 比率）

アラート一次対応は `docs/runbook.md` を参照する。

## 7. 集計ルール（母数と除外）

### 7.1 HTTP 系 SLI
- 母数: 対象エンドポイントへの全リクエスト
- 失敗計上: 5xx, 502/504, タイムアウト
- 除外: 仕様上正しい 4xx（未認証、認可拒否、対象なし）

### 7.2 非同期系 SLI
- Entitlement/Notification は DB とキューの時刻差分を一次指標とする
- 欠損データがある場合は「良好扱い」にしない（欠損自体を観測異常として扱う）

### 7.3 データ品質
- 時刻同期ずれ（clock skew）が疑われる場合、遅延指標を参考値扱いに切り替える
- 計測パイプライン障害時は SLO 判定を保留し、計測復旧を最優先する

## 8. 実装メトリクスの参照先

実装観点のメトリクス（RED/USE、outbox/backlog、OIDC/account 連携失敗）や計測ポイントは、
アーキテクチャと障害モードに紐付けて管理する。

主要なアプリ固有メトリクス名:
- `gateway.login.total{result}`: `/login` 導線結果（`redirect` / `error`）
- `gateway.account.integration.error.total{code}`: account 連携エラー内訳（`ACCOUNT_TIMEOUT` / `ACCOUNT_BAD_GATEWAY` など）
- `entitlement.command.total{action,result}`: grant/revoke の処理結果件数
- `entitlement.outbox.publish.delay`: outbox publish 遅延（`created_at -> published_at`）
- `entitlement.outbox.backlog.age`: outbox claim 時の滞留時間（`now - created_at`）
- `entitlement.outbox.failed.current`: outbox `FAILED` 現在件数
- `mm.time_to_match`: ticket 作成からマッチ成立までの遅延
- `mm.queue.depth{mode}`: mode ごとの待機件数
- `mm.queue.oldest_age{mode}`: mode ごとの最古待機時間（秒）
- `mm.match.total{result}`: マッチ結果件数（`matched` / `cancelled`）
- `mm.dependency.error.total{type}`: 依存障害件数（Redis/NATS/worker_loop など）
- `notification.delivery.total{result}`: 配信結果（`sent` / `failed` / `retry_scheduled`）
- `notification.delivery.e2e.delay`: End-to-End 遅延（`occurred_at -> sent_at`）
- `notification.backlog.current`: backlog 件数（`PENDING` + lease 切れ `PROCESSING`）
- `notification.dlq.total`: DLQ へ隔離した累計件数

HTTP 系 SLI（Gateway-BFF / Account）の成功率・レイテンシは `http.server.requests`（uri / status / exception タグ）を一次指標として集計する。

注記:
- Matchmaking の `SLI-A` は `http.server.requests{uri="/v1/matchmaking/queues/{mode}/tickets"}` を一次指標として集計する。
- Matchmaking の `SLI-B` は `mm.time_to_match` を一次指標として集計する。

- 構成とデータフロー: `docs/architecture.md`
- 監視対象と異常パターン: `docs/failure-modes.md`
- 一次対応手順: `docs/runbook.md`

## 9. 運用レビュー

- 週次: 直近 7 日の burn rate と上位障害要因を確認
- 月次: 28 日判定で SLO 達成状況をレビュー
- 変更時: API 契約、認証方式、主要依存先の変更時に SLO を再評価する

## 10. 関連ドキュメント

- 設計の前提: `docs/architecture.md`
- 障害カタログ: `docs/failure-modes.md`
- 復旧手順: `docs/runbook.md`
