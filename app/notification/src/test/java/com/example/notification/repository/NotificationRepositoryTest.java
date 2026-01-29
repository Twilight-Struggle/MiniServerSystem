/*
 * どこで: Notification リポジトリの統合テスト
 * 何を: markSent/markRetry/しきい値系クエリを Postgres で検証する
 * なぜ: ステータス遷移と閾値境界が DB 方言で崩れないことを保証するため
 */
package com.example.notification.repository;

import com.example.notification.AbstractPostgresContainerTest;
import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
class NotificationRepositoryTest extends AbstractPostgresContainerTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-17T00:00:00Z");
    private static final Duration THRESHOLD_GAP = Duration.ofHours(1);
    private static final String LOCKED_BY = "test-worker";
    private static final int DEFAULT_ATTEMPT_COUNT = 0;
    private static final int INITIAL_ATTEMPT_COUNT = 1;
    private static final int RETRY_ATTEMPT_COUNT = 2;
    private static final String PAYLOAD_JSON = "{}";

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM notifications", new MapSqlParameterSource());
    }

    @Test
    void markSentClearsLockAndRetryFields() {
        NotificationRecord record = processingRecord(BASE_TIME);
        notificationRepository.insert(record);
        Instant sentAt = BASE_TIME.plus(THRESHOLD_GAP);

        int updated = notificationRepository.markSent(record.notificationId(), sentAt, LOCKED_BY);

        assertThat(updated).isEqualTo(1);

        Map<String, Object> row = fetchRow(record.notificationId());
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(getInstant(row, "sent_at")).isEqualTo(sentAt);
        assertThat(row.get("locked_by")).isNull();
        assertThat(getInstant(row, "locked_at")).isNull();
        assertThat(getInstant(row, "lease_until")).isNull();
        assertThat(getInstant(row, "next_retry_at")).isNull();
        assertThat(getInt(row, "attempt_count")).isEqualTo(INITIAL_ATTEMPT_COUNT);
    }

    @Test
    void markRetrySchedulesNextAttemptAndUnlocks() {
        NotificationRecord record = processingRecord(BASE_TIME);
        notificationRepository.insert(record);
        Instant nextRetryAt = BASE_TIME.plus(THRESHOLD_GAP);

        int updated = notificationRepository.markRetry(
                record.notificationId(),
                RETRY_ATTEMPT_COUNT,
                nextRetryAt,
                false,
                LOCKED_BY);

        assertThat(updated).isEqualTo(1);

        Map<String, Object> row = fetchRow(record.notificationId());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(getInt(row, "attempt_count")).isEqualTo(RETRY_ATTEMPT_COUNT);
        assertThat(getInstant(row, "next_retry_at")).isEqualTo(nextRetryAt);
        assertThat(row.get("locked_by")).isNull();
        assertThat(getInstant(row, "locked_at")).isNull();
        assertThat(getInstant(row, "lease_until")).isNull();
    }

    @Test
    void markRetryMarksFailedAndClearsNextRetry() {
        NotificationRecord record = processingRecord(BASE_TIME);
        notificationRepository.insert(record);
        Instant nextRetryAt = BASE_TIME.plus(THRESHOLD_GAP);

        int updated = notificationRepository.markRetry(
                record.notificationId(),
                RETRY_ATTEMPT_COUNT,
                nextRetryAt,
                true,
                LOCKED_BY);

        assertThat(updated).isEqualTo(1);

        Map<String, Object> row = fetchRow(record.notificationId());
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(getInt(row, "attempt_count")).isEqualTo(RETRY_ATTEMPT_COUNT);
        assertThat(getInstant(row, "next_retry_at")).isNull();
        assertThat(row.get("locked_by")).isNull();
        assertThat(getInstant(row, "locked_at")).isNull();
        assertThat(getInstant(row, "lease_until")).isNull();
    }

    @Test
    void insertStoresInstantFields() {
        Instant occurredAt = BASE_TIME.minus(THRESHOLD_GAP);
        Instant createdAt = BASE_TIME;
        Instant sentAt = BASE_TIME.plus(THRESHOLD_GAP);
        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u_1",
                "EntitlementGranted",
                occurredAt,
                PAYLOAD_JSON,
                NotificationStatus.SENT,
                null,
                null,
                null,
                DEFAULT_ATTEMPT_COUNT,
                null,
                createdAt,
                sentAt);

        notificationRepository.insert(record);

        // Instant のバインドが失敗しないことを、保存結果で確認する
        Map<String, Object> row = fetchRow(record.notificationId());
        assertThat(getInstant(row, "occurred_at")).isEqualTo(occurredAt);
        assertThat(getInstant(row, "created_at")).isEqualTo(createdAt);
        assertThat(getInstant(row, "sent_at")).isEqualTo(sentAt);
    }

    @Test
    void countStaleActiveCountsOnlyOlderPendingAndProcessing() {
        Instant threshold = BASE_TIME;
        Instant older = BASE_TIME.minus(THRESHOLD_GAP);
        Instant newer = BASE_TIME.plus(THRESHOLD_GAP);

        List<UUID> staleRecords = new ArrayList<>();
        staleRecords.add(insertRecord(NotificationStatus.PENDING, older));
        staleRecords.add(insertRecord(NotificationStatus.PROCESSING, older));

        insertRecord(NotificationStatus.PENDING, threshold);
        insertRecord(NotificationStatus.PROCESSING, newer);
        insertRecord(NotificationStatus.SENT, older);
        insertRecord(NotificationStatus.FAILED, older);

        int count = notificationRepository.countStaleActive(threshold);

        assertThat(count).isEqualTo(staleRecords.size());
    }

    @Test
    void deleteSentOrFailedOlderThanRemovesOnlyOlderSentAndFailed() {
        Instant threshold = BASE_TIME;
        Instant older = BASE_TIME.minus(THRESHOLD_GAP);
        Instant newer = BASE_TIME.plus(THRESHOLD_GAP);

        List<UUID> sentRecords = new ArrayList<>();
        List<UUID> failedRecords = new ArrayList<>();
        List<UUID> deleteTargets = new ArrayList<>();

        UUID oldSent = insertRecord(NotificationStatus.SENT, older);
        UUID oldFailed = insertRecord(NotificationStatus.FAILED, older);
        deleteTargets.add(oldSent);
        deleteTargets.add(oldFailed);
        sentRecords.add(oldSent);
        failedRecords.add(oldFailed);

        UUID boundarySent = insertRecord(NotificationStatus.SENT, threshold);
        UUID newerFailed = insertRecord(NotificationStatus.FAILED, newer);
        sentRecords.add(boundarySent);
        failedRecords.add(newerFailed);

        insertRecord(NotificationStatus.PENDING, older);
        insertRecord(NotificationStatus.PROCESSING, older);

        int deleted = notificationRepository.deleteSentOrFailedOlderThan(threshold);

        assertThat(deleted).isEqualTo(deleteTargets.size());
        assertThat(countByStatus("SENT")).isEqualTo(sentRecords.size() - 1);
        assertThat(countByStatus("FAILED")).isEqualTo(failedRecords.size() - 1);
        assertThat(countByStatus("PENDING")).isEqualTo(1);
        assertThat(countByStatus("PROCESSING")).isEqualTo(1);
    }

    private NotificationRecord processingRecord(Instant createdAt) {
        return new NotificationRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u_1",
                "EntitlementGranted",
                createdAt,
                PAYLOAD_JSON,
                NotificationStatus.PROCESSING,
                LOCKED_BY,
                createdAt,
                createdAt.plus(THRESHOLD_GAP),
                INITIAL_ATTEMPT_COUNT,
                createdAt,
                createdAt,
                null);
    }

    private UUID insertRecord(NotificationStatus status, Instant createdAt) {
        String lockedBy = status == NotificationStatus.PROCESSING ? LOCKED_BY : null;
        Instant lockedAt = status == NotificationStatus.PROCESSING ? createdAt : null;
        Instant leaseUntil = status == NotificationStatus.PROCESSING ? createdAt.plus(THRESHOLD_GAP) : null;
        Instant nextRetryAt = status == NotificationStatus.PENDING ? createdAt : null;
        Instant sentAt = status == NotificationStatus.SENT ? createdAt : null;
        int attemptCount = status == NotificationStatus.PROCESSING ? INITIAL_ATTEMPT_COUNT : DEFAULT_ATTEMPT_COUNT;

        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u_1",
                "EntitlementGranted",
                createdAt,
                PAYLOAD_JSON,
                status,
                lockedBy,
                lockedAt,
                leaseUntil,
                attemptCount,
                nextRetryAt,
                createdAt,
                sentAt);
        notificationRepository.insert(record);
        return record.notificationId();
    }

    private Map<String, Object> fetchRow(UUID notificationId) {
        String sql = """
                SELECT status,
                       occurred_at,
                       created_at,
                       sent_at,
                       locked_by,
                       locked_at,
                       lease_until,
                       next_retry_at,
                       attempt_count
                FROM notifications
                WHERE notification_id = :notificationId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("notificationId", notificationId);
        return jdbcTemplate.queryForMap(sql, params);
    }

    private Instant getInstant(Map<String, Object> row, String column) {
        Timestamp timestamp = (Timestamp) row.get(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private int getInt(Map<String, Object> row, String column) {
        Number value = (Number) row.get(column);
        return value == null ? 0 : value.intValue();
    }

    private int countByStatus(String status) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", status);
        Integer result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE status = :status",
                params,
                Integer.class);
        return result == null ? 0 : result;
    }
}
