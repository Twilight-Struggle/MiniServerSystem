/*
 * どこで: OutboxEventRepository の統合テスト
 * 何を: PUBLISHED の保持期限削除を検証する
 * なぜ: 期限切れのみ削除されることを保証するため
 */
package com.example.entitlement.repository;

import com.example.entitlement.AbstractPostgresContainerTest;
import com.example.entitlement.model.OutboxEventRecord;
import com.example.entitlement.model.OutboxStatus;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.common.JdbcTimestampUtils.toTimestamp;

@SpringBootTest
@ActiveProfiles("test")
class OutboxEventRepositoryTest extends AbstractPostgresContainerTest {

    private static final Duration TTL = Duration.ofHours(24);
    private static final Instant BASE_TIME = Instant.parse("2026-01-17T00:00:00Z");

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM outbox_events", new MapSqlParameterSource());
    }

    @Test
    void insertStoresCreatedAt() {
        UUID eventId = UUID.randomUUID();
        Instant createdAt = BASE_TIME.minus(TTL);

        int inserted = outboxEventRepository.insert(
                eventId,
                "EntitlementGranted",
                "user-1:sku-1",
                "{}",
                createdAt);

        assertThat(inserted).isEqualTo(1);
        assertThat(fetchCreatedAt(eventId)).isEqualTo(createdAt);
    }

    @Test
    void deletePublishedOlderThanRemovesOnlyOldPublished() {
        Instant threshold = BASE_TIME.minus(TTL);
        UUID oldPublished = UUID.randomUUID();
        UUID newPublished = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        UUID failed = UUID.randomUUID();

        insertOutboxRow(oldPublished, "PUBLISHED", threshold.minusSeconds(1));
        insertOutboxRow(newPublished, "PUBLISHED", threshold.plusSeconds(1));
        insertOutboxRow(pending, "PENDING", null);
        insertOutboxRow(failed, "FAILED", null);

        int deleted = outboxEventRepository.deletePublishedOlderThan(threshold);

        assertThat(deleted).isEqualTo(1);
        assertThat(countById(oldPublished)).isZero();
        assertThat(countById(newPublished)).isEqualTo(1);
        assertThat(countById(pending)).isEqualTo(1);
        assertThat(countById(failed)).isEqualTo(1);
    }

    @Test
    void claimPendingRespectsRetryTimeAndLeaseAndOrder() {
        Instant now = Instant.now();
        Instant leaseUntil = now.plusSeconds(30);
        String lockedBy = "worker-1";
        UUID pendingReadyOld = UUID.randomUUID();
        UUID pendingReadyNew = UUID.randomUUID();
        UUID pendingFuture = UUID.randomUUID();
        UUID inFlightExpired = UUID.randomUUID();
        UUID inFlightActive = UUID.randomUUID();

        insertClaimableRow(pendingReadyOld, "PENDING", null, now.minusSeconds(20), null, null, null);
        insertClaimableRow(pendingReadyNew, "PENDING", now.minusSeconds(1), now.minusSeconds(10), null, null, null);
        insertClaimableRow(pendingFuture, "PENDING", now.plusSeconds(60), now.minusSeconds(30), null, null, null);
        insertClaimableRow(inFlightExpired, "IN_FLIGHT", null, now.minusSeconds(40), "worker-old",
                now.minusSeconds(40), now.minusSeconds(1));
        insertClaimableRow(inFlightActive, "IN_FLIGHT", null, now.minusSeconds(25), "worker-active",
                now.minusSeconds(25), now.plusSeconds(600));

        List<OutboxEventRecord> claimed = outboxEventRepository.claimPending(3, now, leaseUntil, lockedBy);

        assertThat(claimed).hasSize(3);
        assertThat(claimed.get(0).eventId()).isEqualTo(inFlightExpired);
        assertThat(claimed.get(1).eventId()).isEqualTo(pendingReadyOld);
        assertThat(claimed.get(2).eventId()).isEqualTo(pendingReadyNew);
        assertThat(fetchLockedBy(pendingFuture)).isNull();
        assertThat(fetchLockedBy(inFlightActive)).isEqualTo("worker-active");
        assertThat(fetchLockedBy(inFlightExpired)).isEqualTo(lockedBy);
    }

    @Test
    void claimPendingReclaimsExpiredLeaseFromAnotherWorker() {
        // worker-1 が claim した IN_FLIGHT を、lease 期限切れ後に worker-2 が reclaim できることを確認する。
        UUID eventId = UUID.randomUUID();
        Instant createdAt = BASE_TIME.minusSeconds(60);
        Instant firstNow = BASE_TIME;
        Instant firstLeaseUntil = firstNow.plusSeconds(30);
        String worker1 = "worker-1";
        String worker2 = "worker-2";

        insertClaimableRow(eventId, "PENDING", null, createdAt, null, null, null);

        List<OutboxEventRecord> firstClaim = outboxEventRepository.claimPending(1, firstNow, firstLeaseUntil, worker1);
        assertThat(firstClaim).hasSize(1);
        assertThat(firstClaim.get(0).eventId()).isEqualTo(eventId);
        assertThat(fetchLockedBy(eventId)).isEqualTo(worker1);

        // lease 期限切れを再現し、別 worker による reclaim を試す。
        Instant secondNow = firstLeaseUntil.plusSeconds(1);
        Instant secondLeaseUntil = secondNow.plusSeconds(30);
        List<OutboxEventRecord> secondClaim = outboxEventRepository.claimPending(1, secondNow, secondLeaseUntil, worker2);

        assertThat(secondClaim).hasSize(1);
        assertThat(secondClaim.get(0).eventId()).isEqualTo(eventId);
        assertThat(fetchLockedBy(eventId)).isEqualTo(worker2);
        assertThat(fetchLockedAt(eventId)).isEqualTo(secondNow);
        assertThat(fetchLeaseUntil(eventId)).isEqualTo(secondLeaseUntil);
    }

    @Test
    void markPublishedRespectsLockedBy() {
        UUID eventId = UUID.randomUUID();
        Instant createdAt = BASE_TIME;
        Instant lockedAt = BASE_TIME.minusSeconds(10);
        insertClaimableRow(eventId, "IN_FLIGHT", null, createdAt, "worker-1", lockedAt, lockedAt.plusSeconds(30));

        int updated = outboxEventRepository.markPublished(eventId, "worker-2", BASE_TIME);
        assertThat(updated).isZero();
        assertThat(fetchStatus(eventId)).isEqualTo("IN_FLIGHT");
        assertThat(fetchLockedBy(eventId)).isEqualTo("worker-1");
        int updatedMatch = outboxEventRepository.markPublished(eventId, "worker-1", BASE_TIME);
        assertThat(updatedMatch).isEqualTo(1);
        assertThat(fetchStatus(eventId)).isEqualTo("PUBLISHED");
        assertThat(fetchLockedBy(eventId)).isNull();
        assertThat(fetchLockedAt(eventId)).isNull();
        assertThat(fetchLeaseUntil(eventId)).isNull();
    }

    @Test
    void markFailureRespectsLockedBy() {
        UUID eventId = UUID.randomUUID();
        Instant createdAt = BASE_TIME;
        Instant lockedAt = BASE_TIME.minusSeconds(10);
        insertClaimableRow(eventId, "IN_FLIGHT", null, createdAt, "worker-1", lockedAt, lockedAt.plusSeconds(30));

        int updated = outboxEventRepository.markFailure(eventId, "worker-2", 2, OutboxStatus.FAILED, null, "err");
        assertThat(updated).isZero();
        assertThat(fetchStatus(eventId)).isEqualTo("IN_FLIGHT");
        assertThat(fetchLockedBy(eventId)).isEqualTo("worker-1");
        int updatedMatch = outboxEventRepository.markFailure(eventId, "worker-1", 2, OutboxStatus.FAILED, null, "err");
        assertThat(updatedMatch).isEqualTo(1);
        assertThat(fetchStatus(eventId)).isEqualTo("FAILED");
        assertThat(fetchLockedBy(eventId)).isNull();
        assertThat(fetchLockedAt(eventId)).isNull();
        assertThat(fetchLeaseUntil(eventId)).isNull();
    }

    @Test
    void markFailureStoresEnumStatus() {
        UUID eventId = UUID.randomUUID();
        Instant createdAt = BASE_TIME;
        Instant lockedAt = BASE_TIME.minusSeconds(10);
        insertClaimableRow(eventId, "IN_FLIGHT", null, createdAt, "worker-1", lockedAt, lockedAt.plusSeconds(30));

        int updated = outboxEventRepository.markFailure(eventId, "worker-1", 2, OutboxStatus.PENDING, null, "err");
        assertThat(updated).isEqualTo(1);
        assertThat(fetchStatus(eventId)).isEqualTo("PENDING");
    }

    @Test
    void markFailureClearsNextRetryAtWhenFailed() {
        UUID eventId = UUID.randomUUID();
        Instant createdAt = BASE_TIME;
        Instant lockedAt = BASE_TIME.minusSeconds(10);
        Instant existingRetryAt = BASE_TIME.plusSeconds(120);
        insertClaimableRow(eventId, "IN_FLIGHT", existingRetryAt, createdAt, "worker-1",
                lockedAt, lockedAt.plusSeconds(30));

        int updated = outboxEventRepository.markFailure(eventId, "worker-1", 3, OutboxStatus.FAILED, null, "err");

        assertThat(updated).isEqualTo(1);
        assertThat(fetchStatus(eventId)).isEqualTo("FAILED");
        assertThat(fetchNextRetryAt(eventId)).isNull();
        assertThat(fetchLockedBy(eventId)).isNull();
        assertThat(fetchLockedAt(eventId)).isNull();
        assertThat(fetchLeaseUntil(eventId)).isNull();
    }

    private void insertOutboxRow(UUID eventId, String status, Instant publishedAt) {
        // publish対象外/対象を明確にするため、必要なカラムだけを明示的に投入する。
        String sql = """
                INSERT INTO outbox_events (
                  event_id,
                  event_type,
                  aggregate_key,
                  payload,
                  status,
                  attempt_count,
                  created_at,
                  published_at
                ) VALUES (
                  :eventId,
                  :eventType,
                  :aggregateKey,
                  :payload::jsonb,
                  :status,
                  0,
                  :createdAt,
                  :publishedAt
                )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("eventType", "EntitlementGranted")
                .addValue("aggregateKey", "user-1:sku-1")
                .addValue("payload", "{}")
                .addValue("status", status)
                .addValue("createdAt", toTimestamp(BASE_TIME))
                .addValue("publishedAt", toTimestamp(publishedAt));
        jdbcTemplate.update(sql, params);
    }

    private void insertClaimableRow(
            UUID eventId,
            String status,
            Instant nextRetryAt,
            Instant createdAt,
            String lockedBy,
            Instant lockedAt,
            Instant leaseUntil) {
        String sql = """
                INSERT INTO outbox_events (
                  event_id,
                  event_type,
                  aggregate_key,
                  payload,
                  status,
                  attempt_count,
                  next_retry_at,
                  created_at,
                  locked_by,
                  locked_at,
                  lease_until
                ) VALUES (
                  :eventId,
                  :eventType,
                  :aggregateKey,
                  :payload::jsonb,
                  :status,
                  0,
                  :nextRetryAt,
                  :createdAt,
                  :lockedBy,
                  :lockedAt,
                  :leaseUntil
                )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("eventType", "EntitlementGranted")
                .addValue("aggregateKey", "user-1:sku-1")
                .addValue("payload", "{}")
                .addValue("status", status)
                .addValue("nextRetryAt", toTimestamp(nextRetryAt))
                .addValue("createdAt", toTimestamp(createdAt))
                .addValue("lockedBy", lockedBy)
                .addValue("lockedAt", toTimestamp(lockedAt))
                .addValue("leaseUntil", toTimestamp(leaseUntil));
        jdbcTemplate.update(sql, params);
    }

    private int countById(UUID eventId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventId", eventId);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_id = :eventId",
                params,
                Integer.class);
        return count == null ? 0 : count;
    }

    private String fetchStatus(UUID eventId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventId", eventId);
        return jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE event_id = :eventId",
                params,
                String.class);
    }

    private String fetchLockedBy(UUID eventId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventId", eventId);
        return jdbcTemplate.queryForObject(
                "SELECT locked_by FROM outbox_events WHERE event_id = :eventId",
                params,
                String.class);
    }

    private Instant fetchCreatedAt(UUID eventId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventId", eventId);
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT created_at FROM outbox_events WHERE event_id = :eventId",
                params,
                Timestamp.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Instant fetchLockedAt(UUID eventId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventId", eventId);
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT locked_at FROM outbox_events WHERE event_id = :eventId",
                params,
                Timestamp.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Instant fetchNextRetryAt(UUID eventId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventId", eventId);
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM outbox_events WHERE event_id = :eventId",
                params,
                Timestamp.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Instant fetchLeaseUntil(UUID eventId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventId", eventId);
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT lease_until FROM outbox_events WHERE event_id = :eventId",
                params,
                Timestamp.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

}
