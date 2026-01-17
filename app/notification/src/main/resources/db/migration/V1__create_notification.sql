CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
  notification_id UUID PRIMARY KEY,
  event_id UUID NOT NULL,
  user_id TEXT NOT NULL,
  type TEXT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  payload_json JSONB NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED')),
  locked_by TEXT,
  locked_at TIMESTAMPTZ,
  lease_until TIMESTAMPTZ,
  attempt_count INT NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ
);

CREATE INDEX notifications_pending_idx ON notifications (status, next_retry_at, created_at);
CREATE INDEX notifications_processing_idx ON notifications (status, lease_until, created_at);
CREATE INDEX notifications_user_idx ON notifications (user_id, created_at);
CREATE INDEX notifications_occurred_at_idx ON notifications (occurred_at);
