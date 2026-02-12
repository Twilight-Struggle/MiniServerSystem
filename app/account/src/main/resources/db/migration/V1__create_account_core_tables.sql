-- どこで: account の初期スキーマ定義
-- 何を: users / identities / account_roles / audit_logs を作成する
-- なぜ: OIDC 同定、RBAC、監査ログを永続化するため

CREATE TABLE users (
  user_id TEXT PRIMARY KEY,
  display_name TEXT,
  locale TEXT,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE identities (
  provider TEXT NOT NULL,
  subject TEXT NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(user_id),
  email TEXT,
  email_verified BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (provider, subject)
);

CREATE TABLE account_roles (
  user_id TEXT NOT NULL REFERENCES users(user_id),
  role TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role)
);

CREATE TABLE audit_logs (
  id TEXT PRIMARY KEY,
  actor_user_id TEXT NOT NULL,
  action TEXT NOT NULL,
  target_user_id TEXT NOT NULL,
  metadata_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX identities_user_id_idx ON identities (user_id);
CREATE INDEX users_status_idx ON users (status);
CREATE INDEX audit_logs_target_created_idx ON audit_logs (target_user_id, created_at DESC);
