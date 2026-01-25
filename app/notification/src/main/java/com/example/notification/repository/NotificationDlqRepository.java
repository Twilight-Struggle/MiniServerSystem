/*
 * どこで: Notification データアクセス
 * 何を: notification_dlq の登録を担う
 * なぜ: 完全失敗イベントを隔離して運用介入を可能にするため
 */
package com.example.notification.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import static com.example.common.JdbcTimestampUtils.toTimestamp;

@Repository
@RequiredArgsConstructor
public class NotificationDlqRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public void insert(UUID dlqId,
      UUID notificationId,
      UUID eventId,
      String payloadJson,
      String errorMessage,
      Instant createdAt) {
    String sql = """
        INSERT INTO notification_dlq (
          dlq_id,
          notification_id,
          event_id,
          payload_json,
          error_message,
          created_at
        ) VALUES (
          :dlqId,
          :notificationId,
          :eventId,
          :payloadJson::jsonb,
          :errorMessage,
          :createdAt
        )
        ON CONFLICT (notification_id) DO NOTHING
        """;
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("dlqId", dlqId)
        .addValue("notificationId", notificationId)
        .addValue("eventId", eventId)
        .addValue("payloadJson", payloadJson)
        .addValue("errorMessage", errorMessage)
        .addValue("createdAt", toTimestamp(createdAt));
    jdbcTemplate.update(sql, params);
  }

  public int countByEventId(UUID eventId) {
    String sql = "SELECT COUNT(*) FROM notification_dlq WHERE event_id = :eventId";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventId", eventId);
    Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
    return count == null ? 0 : count;
  }
}
