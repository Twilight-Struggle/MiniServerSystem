-- どこで: Entitlement マイグレーション
-- 何を: entitlement_audit テーブルを作成する
-- なぜ: 付与/剥奪の操作履歴を保持するため
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
