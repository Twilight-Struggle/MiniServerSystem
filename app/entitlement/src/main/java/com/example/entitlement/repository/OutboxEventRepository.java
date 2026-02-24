/*
 * どこで: Entitlement データアクセス
 * 何を: outbox_events の登録/claim/状態更新を担う
 * なぜ: Outbox パターンの publish 処理を支えるため
 */
package com.example.entitlement.repository;

import static com.example.common.JdbcTimestampUtils.toTimestamp;

import com.example.entitlement.model.OutboxEventRecord;
import com.example.entitlement.model.OutboxStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxEventRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public OutboxEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    // SpotBugs の EI_EXPOSE_REP2 対応: 外部参照を直接保持せず、ラッパを作り直す
    this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getJdbcTemplate());
  }

  public int insert(
      UUID eventId, String eventType, String aggregateKey, String payloadJson, Instant createdAt) {
    final String sql =
        """
        INSERT INTO outbox_events (
          event_id,
          event_type,
          aggregate_key,
          payload,
          status,
          attempt_count,
          next_retry_at,
          created_at
        ) VALUES (
          :eventId,
          :eventType,
          :aggregateKey,
          :payload::jsonb,
          'PENDING',
          0,
          NULL,
          :createdAt
        )
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("eventId", eventId)
            .addValue("eventType", eventType)
            .addValue("aggregateKey", aggregateKey)
            .addValue("payload", payloadJson)
            .addValue("createdAt", toTimestamp(createdAt));
    return jdbcTemplate.update(sql, params);
  }

  public List<OutboxEventRecord> claimPending(
      int limit, Instant now, Instant leaseUntil, String lockedBy) {
    // PENDING とリース切れの IN_FLIGHT をまとめて claim する
    final String sql =
        """
        WITH cte AS (
          SELECT event_id
          FROM outbox_events
          WHERE (
            status = 'PENDING'
            AND (next_retry_at IS NULL OR next_retry_at <= :now)
          )
          OR (
            status = 'IN_FLIGHT'
            AND (lease_until IS NULL OR lease_until <= :now)
          )
          ORDER BY created_at
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
        )
        UPDATE outbox_events e
        SET status = 'IN_FLIGHT',
            locked_by = :lockedBy,
            locked_at = :now,
            lease_until = :leaseUntil,
            last_error = NULL
        FROM cte
        WHERE e.event_id = cte.event_id
        RETURNING e.event_id, e.event_type, e.aggregate_key, e.payload::text AS payload_text, e.attempt_count
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("now", toTimestamp(now))
            .addValue("leaseUntil", toTimestamp(leaseUntil))
            .addValue("lockedBy", lockedBy)
            .addValue("limit", limit);
    return jdbcTemplate.query(sql, params, this::mapRow);
  }

  public int markPublished(UUID eventId, String lockedBy, Instant publishedAt) {
    final String sql =
        """
        UPDATE outbox_events
        SET status = 'PUBLISHED',
            published_at = :publishedAt,
            locked_by = NULL,
            locked_at = NULL,
            lease_until = NULL
        WHERE event_id = :eventId
          AND locked_by = :lockedBy
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("publishedAt", toTimestamp(publishedAt))
            .addValue("eventId", eventId)
            .addValue("lockedBy", lockedBy);
    return jdbcTemplate.update(sql, params);
  }

  public int markFailure(
      UUID eventId,
      String lockedBy,
      int attemptCount,
      OutboxStatus status,
      Instant nextRetryAt,
      String lastError) {
    final String sql =
        """
        UPDATE outbox_events
        SET attempt_count = :attemptCount,
            status = :status,
            next_retry_at = :nextRetryAt,
            locked_by = NULL,
            locked_at = NULL,
            lease_until = NULL,
            last_error = :lastError
        WHERE event_id = :eventId
          AND locked_by = :lockedBy
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("attemptCount", attemptCount)
            // enum -> DB文字列を固定し、層内で不正値を防ぐ
            .addValue("status", status.name())
            .addValue("nextRetryAt", toTimestamp(nextRetryAt))
            .addValue("lastError", lastError)
            .addValue("eventId", eventId)
            .addValue("lockedBy", lockedBy);
    return jdbcTemplate.update(sql, params);
  }

  public int deletePublishedOlderThan(Instant threshold) {
    // publish 済みのものだけを対象にし、未送信/失敗は残す。
    final String sql =
        """
        DELETE FROM outbox_events
        WHERE status = 'PUBLISHED'
          AND published_at <= :threshold
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("threshold", toTimestamp(threshold));
    return jdbcTemplate.update(sql, params);
  }

  public int countFailed() {
    final String sql = "SELECT COUNT(*) FROM outbox_events WHERE status = 'FAILED'";
    final Integer count =
        jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
    return count == null ? 0 : count;
  }

  private OutboxEventRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new OutboxEventRecord(
        UUID.fromString(rs.getString("event_id")),
        rs.getString("event_type"),
        rs.getString("aggregate_key"),
        rs.getString("payload_text"),
        rs.getInt("attempt_count"));
  }
}
