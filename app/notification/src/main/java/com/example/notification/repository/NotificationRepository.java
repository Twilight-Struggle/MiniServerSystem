/*
 * どこで: Notification データアクセス
 * 何を: notifications テーブルの登録/取得/更新を担う
 * なぜ: 配信処理とデバッグ API を支えるため
 */
package com.example.notification.repository;

import static com.example.common.JdbcTimestampUtils.toTimestamp;

import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public UUID insert(NotificationRecord record) {
    final String sql =
        """
        INSERT INTO notifications (
          notification_id,
          event_id,
          user_id,
          type,
          occurred_at,
          payload_json,
          status,
          locked_by,
          locked_at,
          lease_until,
          attempt_count,
          next_retry_at,
          created_at,
          sent_at
        ) VALUES (
          :notificationId,
          :eventId,
          :userId,
          :type,
          :occurredAt,
          :payloadJson::jsonb,
          :status,
          :lockedBy,
          :lockedAt,
          :leaseUntil,
          :attemptCount,
          :nextRetryAt,
          :createdAt,
          :sentAt
        )
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("notificationId", record.notificationId())
            .addValue("eventId", record.eventId())
            .addValue("userId", record.userId())
            .addValue("type", record.type())
            .addValue("occurredAt", toTimestamp(record.occurredAt()))
            .addValue("payloadJson", record.payloadJson())
            .addValue("status", record.status().name())
            .addValue("lockedBy", record.lockedBy())
            .addValue("lockedAt", toTimestamp(record.lockedAt()))
            .addValue("leaseUntil", toTimestamp(record.leaseUntil()))
            .addValue("attemptCount", record.attemptCount())
            .addValue("nextRetryAt", toTimestamp(record.nextRetryAt()))
            .addValue("createdAt", toTimestamp(record.createdAt()))
            .addValue("sentAt", toTimestamp(record.sentAt()));
    jdbcTemplate.update(sql, params);
    return record.notificationId();
  }

  public List<NotificationRecord> findByUserId(String userId) {
    final String sql =
        """
        SELECT notification_id, event_id, user_id, type, occurred_at, payload_json::text AS payload_json_text, status,
               locked_by, locked_at, lease_until,
               attempt_count, next_retry_at, created_at, sent_at
        FROM notifications
        WHERE user_id = :userId
        ORDER BY created_at DESC
        """;
    final MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId);
    return jdbcTemplate.query(sql, params, this::mapRow);
  }

  public List<NotificationRecord> findPendingForUpdate(int limit, Instant now) {
    final String sql =
        """
        SELECT notification_id, event_id, user_id, type, occurred_at, payload_json::text AS payload_json_text, status,
               locked_by, locked_at, lease_until,
               attempt_count, next_retry_at, created_at, sent_at
        FROM notifications
        WHERE status = 'PENDING'
          AND (next_retry_at IS NULL OR next_retry_at <= :now)
        ORDER BY created_at
        LIMIT :limit
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("now", toTimestamp(now)).addValue("limit", limit);
    return jdbcTemplate.query(sql, params, this::mapRow);
  }

  public List<NotificationRecord> claimPendingForUpdate(
      int limit, Instant now, Instant leaseUntil, String lockedBy) {
    // PENDING と lease 切れの PROCESSING をまとめて claim し、競合を避ける
    final String sql =
        """
        WITH cte AS (
          SELECT notification_id
          FROM notifications
          WHERE (
            status = 'PENDING'
            AND (next_retry_at IS NULL OR next_retry_at <= :now)
          )
          OR (
            status = 'PROCESSING'
            AND (lease_until IS NULL OR lease_until <= :now)
          )
          ORDER BY created_at
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
        )
        UPDATE notifications n
        SET status = 'PROCESSING',
            locked_by = :lockedBy,
            locked_at = :now,
            lease_until = :leaseUntil
        FROM cte
        WHERE n.notification_id = cte.notification_id
        RETURNING n.notification_id, n.event_id, n.user_id, n.type, n.occurred_at,
                  n.payload_json::text AS payload_json_text, n.status,
                  n.locked_by, n.locked_at, n.lease_until,
                  n.attempt_count, n.next_retry_at, n.created_at, n.sent_at
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("now", toTimestamp(now))
            .addValue("leaseUntil", toTimestamp(leaseUntil))
            .addValue("lockedBy", lockedBy)
            .addValue("limit", limit);
    return jdbcTemplate.query(sql, params, this::mapRow);
  }

  public int markSent(UUID notificationId, Instant sentAt, String lockedBy) {
    final String sql =
        """
        UPDATE notifications
        SET status = 'SENT',
            sent_at = :sentAt,
            next_retry_at = NULL,
            locked_by = NULL,
            locked_at = NULL,
            lease_until = NULL
        WHERE notification_id = :notificationId
          AND status = 'PROCESSING'
          AND locked_by = :lockedBy
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("sentAt", toTimestamp(sentAt))
            .addValue("notificationId", notificationId)
            .addValue("lockedBy", lockedBy);
    return jdbcTemplate.update(sql, params);
  }

  public int markRetry(
      UUID notificationId, int attemptCount, Instant nextRetryAt, boolean failed, String lockedBy) {
    final String sql =
        """
        UPDATE notifications
        SET status = :status,
            attempt_count = :attemptCount,
            next_retry_at = :nextRetryAt,
            locked_by = NULL,
            locked_at = NULL,
            lease_until = NULL
        WHERE notification_id = :notificationId
          AND status = 'PROCESSING'
          AND locked_by = :lockedBy
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("status", failed ? "FAILED" : "PENDING")
            .addValue("attemptCount", attemptCount)
            .addValue("nextRetryAt", failed ? null : toTimestamp(nextRetryAt))
            .addValue("notificationId", notificationId)
            .addValue("lockedBy", lockedBy);
    return jdbcTemplate.update(sql, params);
  }

  public int deleteSentOrFailedOlderThan(Instant threshold) {
    final String sql =
        """
        DELETE FROM notifications
        WHERE created_at < :threshold
          AND status IN ('SENT', 'FAILED')
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("threshold", toTimestamp(threshold));
    return jdbcTemplate.update(sql, params);
  }

  public int countStaleActive(Instant threshold) {
    final String sql =
        """
        SELECT COUNT(*)
        FROM notifications
        WHERE created_at < :threshold
          AND status IN ('PENDING', 'PROCESSING')
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("threshold", toTimestamp(threshold));
    final Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
    return count == null ? 0 : count;
  }

  private NotificationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new NotificationRecord(
        UUID.fromString(rs.getString("notification_id")),
        UUID.fromString(rs.getString("event_id")),
        rs.getString("user_id"),
        rs.getString("type"),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getString("payload_json_text"),
        NotificationStatus.valueOf(rs.getString("status")),
        rs.getString("locked_by"),
        toInstant(rs.getTimestamp("locked_at")),
        toInstant(rs.getTimestamp("lease_until")),
        rs.getInt("attempt_count"),
        toInstant(rs.getTimestamp("next_retry_at")),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("sent_at")));
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
