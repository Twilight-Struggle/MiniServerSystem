# SLO (Service Level Objectives)

このドキュメントは MiniServerSystem の信頼性目標（SLO）と測定指標（SLI）を定義する。
目的は「ユーザー影響ベースで守るべき数値目標」を明確にし、運用判断に使える状態を作ること。

## 1. この文書の責務

扱う内容:
- SLO/SLI の定義
- エラーバジェット
- アラート評価の方針

扱わない内容:
- システム構成/データフロー（`docs/architecture.md`）
- 障害モードの詳細分類（`docs/failure-modes.md`）
- 復旧オペレーション（`docs/runbook.md`）

## 2. 対象サービス

- Gateway-BFF
- Entitlement
- Matchmaking
- Notification

期間は 28 日ローリングを基本とする。

## 3. SLO / SLI 定義

### 3.1 Gateway-BFF
- SLI: HTTP 成功率（`2xx + 3xx`）
- SLO: 99.9% / 28日

- SLI: レイテンシ（主要 API の p95）
- SLO: p95 < 300ms / 28日

### 3.2 Entitlement
- SLI: 権利付与/剥奪処理の成功率
- SLO: 99.95% / 28日

- SLI: 反映遅延（付与完了から照会反映までの p95）
- SLO: p95 < 1s / 28日

- SLI: Outbox publish 遅延（`created_at -> published_at` の p95）
- SLO: p95 < 5s / 28日

- SLI: Outbox 滞留（`now - created_at` の p95）
- SLO: p95 < 10s / 28日

- SLI: Outbox failed 件数
- SLO: 0 件を維持

### 3.3 Matchmaking
- SLI: キュー参加 API 成功率
- SLO: 99.9% / 28日

- SLI: Time-to-Match（`queue_joined_at -> matched_at` の p95）
- SLO: p95 < 10s / 28日

### 3.4 Notification
- SLI: 通知配信成功率（`SENT / (SENT + FAILED)`）
- SLO: 99.9% / 28日

- SLI: End-to-End 遅延（`event occurred_at -> notification sent_at` の p95）
- SLO: p95 < 30s / 28日

- SLI: Backlog（`PENDING` + lease 切れ `PROCESSING`）
- SLO: 平常時ゼロ近傍、異常時は単調増加を継続させない

- SLI: DLQ 発生件数
- SLO: 0 件を維持

## 4. エラーバジェット

- 99.9%: 28日で約43分
- 99.95%: 28日で約21分

運用ルール:
- バジェット急消費時は新規変更を抑制し、復旧と再発防止を優先する
- バジェット消費の根拠はインシデント記録に残す

## 5. アラート方針

即時対応:
- Gateway 5分窓で成功率低下（件数閾値と併用）
- Notification で DLQ 発生
- Notification backlog が継続的に増加

追跡対応:
- 60分窓で SLO 未達傾向
- Entitlement outbox 遅延の継続
- Matchmaking time-to-match 劣化の継続

アラートの一次対応は `docs/runbook.md` を参照する。

## 6. 計測実装の参照先

実装観点のメトリクス（RED/USE、通知専用メトリクス）や計測ポイントは、
アーキテクチャと障害モードに紐付けて管理する。

- 構成とデータフロー: `docs/architecture.md`
- 監視対象と異常パターン: `docs/failure-modes.md`

## 7. 関連ドキュメント

- 設計の前提: `docs/architecture.md`
- 障害カタログ: `docs/failure-modes.md`
- 復旧手順: `docs/runbook.md`
