-- どこで: Notification マイグレーション
-- 何を: JetStream MaxDeliver advisory の DLQ テーブルを作成する
-- なぜ: 再処理用に stream_seq を保持するため
CREATE TABLE notification_nats_dlq (
  stream_seq BIGINT PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
