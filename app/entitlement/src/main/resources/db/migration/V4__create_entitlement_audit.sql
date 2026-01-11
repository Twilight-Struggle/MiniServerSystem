CREATE TABLE entitlement_audit (
  audit_id    UUID PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  user_id     TEXT NOT NULL,
  stock_keeping_unit         TEXT NOT NULL,
  action      TEXT NOT NULL CHECK (action IN ('GRANT','REVOKE')),
  source      TEXT,
  source_id   TEXT,
  request_id  TEXT,
  detail      JSONB
);

CREATE INDEX entitlement_audit_user_idx ON entitlement_audit (user_id, occurred_at);
