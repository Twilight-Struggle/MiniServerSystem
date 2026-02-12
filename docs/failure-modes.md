# Failure Modes

このドキュメントは MiniServerSystem の代表的な障害モードを定義する。
目的は「何が壊れるか」「何を見れば検知できるか」「どこまでをここで決めるか」を明確にすること。

## 1. この文書の責務

扱う内容:
- 障害モードの分類
- ユーザー影響/SLO影響
- 検知シグナル
- 緩和方針

扱わない内容:
- 具体的なオペレーション手順や SQL（`docs/runbook.md`）
- SLO の閾値定義（`docs/slo.md`）
- システム全体構成（`docs/architecture.md`）

## 2. 読み方

各障害モードは以下で記述する:
- ID
- 症状
- 影響
- 検知
- 緩和方針

## 3. 障害モード一覧

### 3.1 データ層（Postgres）

#### FM-DB-01: 接続枯渇 / スロークエリ / ロック競合
- 症状: claim/update が遅延し、バックログが増える
- 影響: Notification 遅延、API タイムアウト増、SLO 悪化
- 検知: DB ロック待ち増、クエリ遅延増、アプリ側タイムアウト増
- 緩和方針: ワーカー並列度抑制、遅いクエリ特定、インデックス/クエリ改善

#### FM-DB-02: 冪等テーブル肥大（retention 不備）
- 症状: `processed_events` 肥大で挿入/検索遅延
- 影響: 消費処理遅延、backlog 増加
- 検知: テーブルサイズ増、retention 削除件数が継続的に 0
- 緩和方針: retention 設定見直し、定期実行の監視追加

### 3.2 Outbox Relay（Entitlement -> JetStream）

#### FM-OB-01: publish 停滞（Outbox バックログ増）
- 症状: outbox が PENDING/IN_FLIGHT のまま滞留
- 影響: 通知が生成されない、遅延拡大
- 検知: outbox backlog 増、`attempt_count` 増、publish error 増
- 緩和方針: ワーカー健全性確認、NATS 健全性確認、lease/リトライ設定見直し

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

### 3.3 NATS JetStream / Subscriber

#### FM-NATS-01: consumer lag 増加
- 症状: stream 側にメッセージ滞留
- 影響: 通知遅延の連鎖
- 検知: consumer lag 増、Notification 処理遅延増
- 緩和方針: consumer スケール、DB 側ボトルネック同時調査

#### FM-NATS-02: 受信 payload 解析失敗
- 症状: decode 例外で処理不可
- 影響: 当該イベント処理失敗、契約不整合リスク
- 検知: 解析失敗件数増
- 緩和方針: 恒久失敗扱いで隔離、契約破壊の発生源を修正

### 3.4 Notification（永続化 + 配信）

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
- 影響: 配信不能の顕在化、問い合わせ増
- 検知: DLQ 件数増、FAILED 比率増
- 緩和方針: 恒久失敗/一時失敗の分類、再処理フロー整備

#### FM-NOTI-05: DLQ と通知状態の不整合
- 症状: DLQ のみ登録、または通知状態のみ更新
- 影響: 監査不能、再処理判断不能
- 検知: DLQ と notifications の突合差分
- 緩和方針: 同一 Tx 保証、ロック喪失時 rollback 徹底

## 4. 不変条件（Invariant）

- I1: 同一 `event_id` は最終的に 1 度のみ有効処理される
- I2: 通知は `SENT` か `FAILED` に収束する（lease 回収前提）
- I3: DLQ 登録と `FAILED` 更新は原子的に扱う
- I4: ロック喪失時に部分的副作用だけを残さない

## 5. 参照関係

- SLO 影響評価の閾値: `docs/slo.md`
- 構成/データフローの前提: `docs/architecture.md`
- 実際の一次対応手順: `docs/runbook.md`
