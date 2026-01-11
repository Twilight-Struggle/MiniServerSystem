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

-- 参照頻度が高い想定なので user_id で引ける索引は必須
CREATE INDEX entitlements_user_idx ON entitlements (user_id);

-- ACTIVEだけを頻繁に取るなら部分索引も有効
CREATE INDEX entitlements_user_active_idx
  ON entitlements (user_id, stock_keeping_unit)
  WHERE status = 'ACTIVE';
