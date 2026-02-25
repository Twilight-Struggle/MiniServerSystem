# Matchmakingの作成
## 想定アプリ
1vs1のマッチを想定する
- クライアント（= gateway-bff 経由）が Join → “ticket” を発行
- ticket が Matched になると、match_id と対戦相手/接続情報（ダミーでもOK）を返す
- Cancel でキュー離脱（または TTL で自動失効）
- matchしたら通知

## API
- POST /v1/matchmaking/queues/{mode}/tickets
    - req: { "party_size": 1, "attributes": {...}, "idempotency_key": "..." }
    - resp: { "ticket_id": "...", "status": "QUEUED", "expires_at": "..." }
- GET /v1/matchmaking/tickets/{ticket_id}
    - resp: QUEUED | MATCHED | CANCELLED | EXPIRED
    - MATCHED の場合: { "match_id": "...", "peer_user_ids": [...], "session": {...} }
- DELETE /v1/matchmaking/tickets/{ticket_id}
    - cancel（冪等：すでに cancel / expired でも 200 で統一）

## データ構造
Redisに乗せる

### ticket
- mm:ticket:{ticketId} = Hash
- user_id, mode(casual, rank), status, created_at, expires_at, attributes(json)(マッチング振り分けの基準となる数字{region, skill, language, latency_bucket等}), match_id?
TTL = expires_at まで（自然回収）

### キュー
- mm:queue:{mode} = Sorted Set
    - member = ticketId
    - score = enqueue_ttimestamp（公平性）

### idempotency
- mm:idemp:{userId}:{mode}:{idempotencyKey} = ticketId（TTL短め）
Joinを複数回送信された時用

## マッチ成立の原始星
キューから1つずつ取り出してmatchedに更新すると原子性が崩れる。
このためRedis Luaスクリプトで、「キューから2件取り出して、ticket の status を MATCHED に更新し、match を生成」を行う。
- EVAL match_two.lua mm:queue:{mode} mm:ticket:{t1} mm:ticket:{t2} ...

## ワーカモデル
- MatchmakerWorker（複数インスタンスOK）
    - 一定間隔で EVAL を叩き、成立した match を返す
    - mm:queue:{mode} を見て、待ちが2件以上あるか確認→先頭2件を取り出す→2つの ticket の status を MATCHED に更新し、match_id を付与→キューから ticket を削除→notificationに通知
スケールは mode/region ごとにキュー分割（key空間分割）で自然に可能(ただし今回はスケールは行わない)

## notification
Entitlement → NATS JetStream → Notificationと同様に、マッチ成立したらNotificationに送る。

## SLO/SLI
- Time-to-Match（最重要）
    - SLI: ticket_created_at → matched_at の p95 / p99
- Join成功率（HTTP 2xx率、validation/認可除外）
- Queue depth / age
    - mm.queue.depth{mode}、mm.queue.oldest_age{mode}
- Match成立率
    - mm.match.total{result=matched|expired|cancelled}
- Redis依存
    - Redis timeout/error、Lua 実行時間（遅いと全体が詰まる）

## failure-modes
- Redis 遅延/停止
- Worker 落ち → キュー滞留
- Cancel と Match が競合 → “cancelled なのに matched” をどう扱う？
    - 基本はLua で「status=QUEUED のものだけマッチ対象」にして防ぐ
- TTL 失効 → queue から消える前に worker が拾う（stale ticket）
    - Lua 側で ticket hash の存在/期限を検証して弾く