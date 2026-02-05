-- どこで: Entitlement マイグレーション
-- 何を: outbox_events テーブルと索引を作成する
-- なぜ: publish/claim とリトライを効率化するため
CREATE TABLE outbox_events (
  event_id      UUID PRIMARY KEY,
  event_type    TEXT NOT NULL,          -- EntitlementGranted / EntitlementRevoked
  aggregate_key TEXT NOT NULL,          -- "user_id:stock_keeping_unit" 等（分散や分析に使う）
  payload       JSONB NOT NULL,

  status        TEXT NOT NULL CHECK (status IN ('PENDING','IN_FLIGHT','PUBLISHED','FAILED')),
  attempt_count INT  NOT NULL DEFAULT 0,

  next_retry_at TIMESTAMPTZ,            -- NULLなら即時
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at  TIMESTAMPTZ,

  -- claim/lease（複数ワーカ対応）
  locked_by     TEXT,
  locked_at     TIMESTAMPTZ,
  lease_until   TIMESTAMPTZ,

  last_error    TEXT
);

-- PENDINGや期限切れIN_FLIGHTを効率よく拾う
CREATE INDEX outbox_pick_idx
  ON outbox_events (status, next_retry_at, created_at);

-- IN_FLIGHT のリース切れ回収を効率化する
CREATE INDEX outbox_inflight_lease_idx
  ON outbox_events (lease_until, created_at)
  WHERE status = 'IN_FLIGHT';

-- PUBLISHED の期限切れ削除を効率化する
CREATE INDEX outbox_published_at_idx
  ON outbox_events (published_at)
  WHERE status = 'PUBLISHED';
