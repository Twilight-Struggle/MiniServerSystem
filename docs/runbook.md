# Runbook

このドキュメントはインシデント時の一次切り分けと復旧手順を定義する。
目的は「誰が対応しても同じ順序で安全に復旧できる」状態を作ること。

## 1. この文書の責務

扱う内容:
- 初動の確認順序
- 症状別のオペレーション
- 復旧後の確認と記録

扱わない内容:
- 障害モードの網羅的説明（`docs/failure-modes.md`）
- SLO 閾値定義（`docs/slo.md`）
- アーキテクチャ説明（`docs/architecture.md`）

## 2. 対象範囲

- Entitlement service（API + Outbox）
- Outbox Relay（DB -> NATS publish）
- NATS JetStream
- Notification service（subscribe -> DB -> delivery worker）
- 依存: PostgreSQL / Redis（採用時）

## 3. 初動フロー

1. 影響確認（ユーザー報告、監視アラート、SLO 逸脱）
2. 詰まり箇所の特定（Outbox / NATS / Notification / DB）
3. 安全な最小復旧（再起動、スロットル）
4. 復旧判定（backlog 減少、エラー率低下、遅延回復）
5. ポストモーテム記録

## 4. 症状別オペレーション

### 4.1 通知が届かない

確認順序:
1. Notification backlog（`PENDING` / `PROCESSING`）
2. NATS subscriber 接続状態
3. Outbox publish 遅延
4. DB ロック待ち/接続枯渇

確認SQL:
```sql
SELECT status, COUNT(*)
FROM notifications
GROUP BY status
ORDER BY status;
```

```sql
SELECT COUNT(*)
FROM notifications
WHERE created_at < NOW() - INTERVAL '10 minutes'
  AND status IN ('PENDING', 'PROCESSING');
```

初期対応:
- worker 停止時は再起動
- DB 高負荷時は worker 並列度を下げる
- NATS 再接続ループ時は認証/ネットワーク設定を確認

### 4.2 重複通知が出る

確認順序:
1. `processed_events` の一意制約有効性
2. 先行 INSERT による冪等化が維持されているか
3. at-least-once 前提で duplicate を吸収できているか

初期対応:
- 重複抑止が壊れている場合は配信処理を一時抑制し、整合性確認後に再開

### 4.3 PROCESSING のまま進まない

確認順序:
1. `PROCESSING` 件数と `lease_until` の分布
2. worker 生存状態
3. `locked_by` 条件で更新失敗していないか

確認SQL:
```sql
SELECT notification_id, event_id, locked_by, locked_at, lease_until
FROM notifications
WHERE status = 'PROCESSING'
ORDER BY locked_at
LIMIT 50;
```

初期対応:
- worker 再起動
- lease 設定の再確認（短すぎ/長すぎ）
- clock skew が疑わしい場合は時刻同期を確認

### 4.4 DLQ / FAILED が増える

確認順序:
1. 恒久失敗の種類（payload 不正、必須フィールド欠落）
2. 一時失敗の種類（外部依存障害）
3. 増加傾向（急増か継続増加か）

初期対応:
- 恒久失敗は生成元修正を優先
- 一時失敗は backoff/レート制御を調整
- 再投入方針（再送/破棄）を事前運用ルールに従い適用

## 5. 復旧定型手順

### 5.1 ワーカー再起動（最小復旧）
1. 対象プロセスの例外頻度を確認
2. Pod/プロセス再起動
3. backlog 減少とエラー率低下を確認

### 5.2 スロットリング
- Notification worker の並列度を下げる
- Outbox Relay の publish レートを下げる

### 5.3 整合性チェック
```sql
SELECT COUNT(*)
FROM notifications
WHERE status = 'SENT'
  AND (locked_by IS NOT NULL OR next_retry_at IS NOT NULL);
```

## 6. 復旧判定

以下を満たしたら一次復旧完了:
- backlog が減少傾向
- エラー率が基準以下に復帰
- DLQ/FAILED が収束
- 主要 SLI が回復傾向

SLO 基準は `docs/slo.md` を参照。

## 7. 事後対応（ポストモーテム）

記録必須項目:
1. どの SLO/SLI にどれだけ影響したか
2. 起点となった障害モード（`docs/failure-modes.md` の ID）
3. 検知から復旧までのタイムライン（TTD/TTR）
4. 恒久対策（計測/制御/設計の改善）

## 8. 参照関係

- SLO 閾値と目標: `docs/slo.md`
- 障害モード分類: `docs/failure-modes.md`
- 構成とデータフロー: `docs/architecture.md`
