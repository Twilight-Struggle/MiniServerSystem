CREATE TABLE idempotency_keys (
  idem_key       TEXT PRIMARY KEY,     -- HeaderのIdempotency-Key
  request_hash   TEXT NOT NULL,        -- 正規化したリクエストから算出
  response_code  INT  NOT NULL,
  response_body  JSONB NOT NULL,

  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idempotency_expires_idx ON idempotency_keys (expires_at);
