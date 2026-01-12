-- どこで: Notification マイグレーション
-- 何を: notification_dlq テーブルを作成する
-- なぜ: 完全失敗イベントを隔離するため
CREATE TABLE notification_dlq (
  dlq_id UUID PRIMARY KEY,
  notification_id UUID NOT NULL,
  event_id UUID NOT NULL,
  payload_json JSONB NOT NULL,
  error_message TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT notification_dlq_notification_id_uniq UNIQUE (notification_id)
);

CREATE INDEX notification_dlq_event_idx ON notification_dlq (event_id, created_at);
