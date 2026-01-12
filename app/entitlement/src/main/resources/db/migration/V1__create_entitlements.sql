-- どこで: Entitlement マイグレーション
-- 何を: entitlements テーブルと索引を作成する
-- なぜ: 付与/剥奪の状態管理と参照を効率化するため
CREATE TABLE entitlements (
  user_id     TEXT   NOT NULL,
  stock_keeping_unit         TEXT   NOT NULL,

  status      TEXT   NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
  granted_at  TIMESTAMPTZ,
  revoked_at  TIMESTAMPTZ,

  source      TEXT,          -- purchase/refund/admin etc なぜ付与されたか後で分かる
  source_id   TEXT,          -- purchase_id etc

  version     BIGINT NOT NULL DEFAULT 0,   -- 更新ごとに+1
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

  PRIMARY KEY (user_id, stock_keeping_unit)
);

CREATE INDEX entitlements_user_updated_idx
  ON entitlements (user_id, updated_at DESC);
