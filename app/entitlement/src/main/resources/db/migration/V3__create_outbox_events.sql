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

-- 分析/追跡用
CREATE INDEX outbox_created_idx ON outbox_events (created_at);
CREATE INDEX outbox_aggregate_idx ON outbox_events (aggregate_key, created_at);
