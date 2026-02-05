/*
 * どこで: ProcessedEventRepository の統合テスト
 * 何を: insertIfAbsent と deleteOlderThan の境界動作を検証する
 * なぜ: 冪等性と保持期間の境界が DB 方言で崩れないことを保証するため
 */
package com.example.notification.repository;

import com.example.notification.AbstractPostgresContainerTest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProcessedEventRepositoryTest extends AbstractPostgresContainerTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-17T00:00:00Z");
    private static final Duration THRESHOLD_GAP = Duration.ofHours(1);
    private static final int EXPECTED_ONE_ROW = 1;
    private static final int EXPECTED_TWO_ROWS = 2;
    private static final int EXPECTED_ONE_DELETED = 1;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        // テスト間でデータを分離し、境界条件を確実に検証する
        jdbcTemplate.update("DELETE FROM processed_events", new MapSqlParameterSource());
    }

    @Test
    void insertIfAbsentReturnsFalseOnDuplicateEventId() {
        // 同一 event_id の 2 回挿入で 2 回目が抑止されることを確認する
        UUID eventId = UUID.randomUUID();
        Instant firstProcessedAt = BASE_TIME;
        Instant secondProcessedAt = BASE_TIME.plus(THRESHOLD_GAP);

        boolean firstInsert = processedEventRepository.insertIfAbsent(eventId, firstProcessedAt);
        boolean secondInsert = processedEventRepository.insertIfAbsent(eventId, secondProcessedAt);

        // 1 回目は成功し、2 回目は重複として拒否されることを確認する
        assertThat(firstInsert).isTrue();
        assertThat(secondInsert).isFalse();
        assertThat(countAll()).isEqualTo(EXPECTED_ONE_ROW);
    }

    @Test
    void deleteOlderThanKeepsBoundaryAndNewerRows() {
        // threshold より古い行だけが削除され、境界と新しい行が残ることを確認する
        Instant threshold = BASE_TIME;
        Instant older = BASE_TIME.minus(THRESHOLD_GAP);
        Instant newer = BASE_TIME.plus(THRESHOLD_GAP);

        UUID olderId = UUID.randomUUID();
        UUID boundaryId = UUID.randomUUID();
        UUID newerId = UUID.randomUUID();

        processedEventRepository.insertIfAbsent(olderId, older);
        processedEventRepository.insertIfAbsent(boundaryId, threshold);
        processedEventRepository.insertIfAbsent(newerId, newer);

        int deleted = processedEventRepository.deleteOlderThan(threshold);

        // 削除件数と残存件数をあわせて境界条件を検証する
        assertThat(deleted).isEqualTo(EXPECTED_ONE_DELETED);
        assertThat(exists(olderId)).isFalse();
        assertThat(exists(boundaryId)).isTrue();
        assertThat(exists(newerId)).isTrue();
        assertThat(countAll()).isEqualTo(EXPECTED_TWO_ROWS);
    }

    private int countAll() {
        // 集計の意図を明確にするため、専用メソッドに切り出しておく
        Integer result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events",
                new MapSqlParameterSource(),
                Integer.class);
        return result == null ? 0 : result;
    }

    private boolean exists(UUID eventId) {
        // event_id の存在だけを確認し、検証対象を明確にする
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId);
        Integer result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = :eventId",
                params,
                Integer.class);
        return result != null && result > 0;
    }
}
