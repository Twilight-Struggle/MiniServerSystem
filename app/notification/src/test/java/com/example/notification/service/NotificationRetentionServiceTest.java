/*
 * Where: Notification retention tests
 * What: Verifies cleanup deletes only eligible rows
 * Why: Prevent accidental removal of active notifications
 */
package com.example.notification.service;

import com.example.notification.AbstractPostgresContainerTest;
import com.example.notification.config.NotificationRetentionProperties;
import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.ProcessedEventRepository;
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
class NotificationRetentionServiceTest extends AbstractPostgresContainerTest {

    @Autowired
    private NotificationRetentionService retentionService;

    @Autowired
    private NotificationRetentionProperties retentionProperties;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM notifications", new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM processed_events", new MapSqlParameterSource());
    }

    @Test
    void cleanupRemovesOnlySentAndFailed() {
        Instant now = Instant.now();
        int retentionDays = retentionProperties.retentionDays();
        Instant threshold = now.minus(Duration.ofDays(retentionDays));
        Instant old = now.minus(Duration.ofDays(retentionDays + 1));
        Instant recent = now.minus(Duration.ofDays(retentionDays - 1));

        processedEventRepository.insertIfAbsent(UUID.randomUUID(), old);
        processedEventRepository.insertIfAbsent(UUID.randomUUID(), recent);

        notificationRepository.insert(notificationRecord(NotificationStatus.SENT, old, old));
        notificationRepository.insert(notificationRecord(NotificationStatus.FAILED, old, null));
        notificationRepository.insert(notificationRecord(NotificationStatus.PENDING, old, null));
        notificationRepository.insert(notificationRecord(NotificationStatus.PROCESSING, old, null));
        notificationRepository.insert(notificationRecord(NotificationStatus.SENT, recent, recent));

        retentionService.cleanup();

        assertThat(count("SELECT COUNT(*) FROM processed_events")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notifications")).isEqualTo(3);
        assertThat(countByStatus("PENDING")).isEqualTo(1);
        assertThat(countByStatus("PROCESSING")).isEqualTo(1);
        assertThat(countByStatus("SENT")).isEqualTo(1);
        assertThat(countByStatus("FAILED")).isEqualTo(0);
        assertThat(count("""
                SELECT COUNT(*)
                FROM notifications
                WHERE status IN ('PENDING', 'PROCESSING')
                  AND created_at < :threshold
                """, threshold)).isEqualTo(2);
    }

    private NotificationRecord notificationRecord(NotificationStatus status, Instant createdAt, Instant sentAt) {
        Instant lockedAt = status == NotificationStatus.PROCESSING ? createdAt : null;
        Instant leaseUntil = status == NotificationStatus.PROCESSING ? createdAt : null;
        String lockedBy = status == NotificationStatus.PROCESSING ? "test-worker" : null;
        Instant nextRetryAt = status == NotificationStatus.PENDING ? createdAt : null;
        Instant occurred_at = createdAt;
        return new NotificationRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u_1",
                "EntitlementGranted",
                occurred_at,
                "{}",
                status,
                lockedBy,
                lockedAt,
                leaseUntil,
                0,
                nextRetryAt,
                createdAt,
                sentAt);
    }

    private int count(String sql) {
        return count(sql, null);
    }

    private int count(String sql, Instant threshold) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (threshold != null) {
            params.addValue("threshold", threshold);
        }
        Integer result = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return result == null ? 0 : result;
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
