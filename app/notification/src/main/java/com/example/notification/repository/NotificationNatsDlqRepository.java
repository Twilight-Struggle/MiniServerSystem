/*
 * どこで: Notification データアクセス
 * 何を: JetStream advisory (MaxDeliver/TERM) の stream_seq を保存する
 * なぜ: 未処理メッセージの再取得に必要な識別子を保持するため
 */
package com.example.notification.repository;

import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import static com.example.common.JdbcTimestampUtils.toTimestamp;

@Repository
@RequiredArgsConstructor
public class NotificationNatsDlqRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void insert(long streamSeq, Instant createdAt) {
        String sql = """
                INSERT INTO notification_nats_dlq (
                  stream_seq,
                  created_at
                ) VALUES (
                  :streamSeq,
                  :createdAt
                )
                ON CONFLICT (stream_seq) DO NOTHING
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("streamSeq", streamSeq)
                .addValue("createdAt", toTimestamp(createdAt));
        jdbcTemplate.update(sql, params);
    }
}
