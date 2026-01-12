/*
 * どこで: Notification データアクセス
 * 何を: processed_events の登録/存在確認を行う
 * なぜ: イベントの冪等性を保証するため
 */
package com.example.notification.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import static com.example.common.JdbcTimestampUtils.toTimestamp;

@Repository
@RequiredArgsConstructor
public class ProcessedEventRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public boolean insertIfAbsent(UUID eventId, Instant processedAt) {
        String sql = """
                INSERT INTO processed_events (event_id, processed_at)
                VALUES (:eventId, :processedAt)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("processedAt", toTimestamp(processedAt));
        try {
            return jdbcTemplate.update(sql, params) > 0;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    public int deleteOlderThan(Instant threshold) {
        String sql = """
                DELETE FROM processed_events
                WHERE processed_at < :threshold
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("threshold", toTimestamp(threshold));
        return jdbcTemplate.update(sql, params);
    }
}
