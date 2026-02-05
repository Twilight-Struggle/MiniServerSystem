/*
 * どこで: Notification テスト
 * 何を: Postgres での claim/lease 動作を検証する
 * なぜ: UPDATE ... RETURNING + SKIP LOCKED の方言差異を統合テストで検証するため
 */
package com.example.notification.repository;

import com.example.notification.AbstractPostgresContainerTest;
import com.example.notification.config.NotificationDeliveryProperties;
import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@SpringBootTest
@ActiveProfiles("test")
class NotificationClaimRepositoryTest extends AbstractPostgresContainerTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryProperties properties;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM notifications", new MapSqlParameterSource());
    }

    @Test
    void claimMovesPendingToProcessingWithLock() {
        Instant now = Instant.now();
        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u_1",
                "EntitlementGranted",
                now,
                "{}",
                NotificationStatus.PENDING,
                null,
                null,
                null,
                0,
                now,
                now,
                null);
        notificationRepository.insert(record);

        Instant leaseUntil = now.plus(properties.lease());
        List<NotificationRecord> claimed = notificationRepository.claimPendingForUpdate(
                properties.batchSize(),
                now,
                leaseUntil,
                "test-host");

        assertThat(claimed).hasSize(1);
        NotificationRecord claimedRecord = claimed.get(0);
        assertThat(claimedRecord.status()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(claimedRecord.lockedBy()).isEqualTo("test-host");
        assertInstantCloseToMicros(leaseUntil, claimedRecord.leaseUntil());
    }

    @Test
    void claimRecoversExpiredProcessing() {
        Instant now = Instant.now();
        Instant expiredLease = now.minus(properties.lease());
        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u_2",
                "EntitlementRevoked",
                now,
                "{}",
                NotificationStatus.PROCESSING,
                "old-host",
                expiredLease,
                expiredLease,
                0,
                now,
                now,
                null);
        notificationRepository.insert(record);

        Instant leaseUntil = now.plus(properties.lease());
        List<NotificationRecord> claimed = notificationRepository.claimPendingForUpdate(
                properties.batchSize(),
                now,
                leaseUntil,
                "recovered-host");

        assertThat(claimed).hasSize(1);
        NotificationRecord claimedRecord = claimed.get(0);
        assertThat(claimedRecord.status()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(claimedRecord.lockedBy()).isEqualTo("recovered-host");
        assertInstantCloseToMicros(leaseUntil, claimedRecord.leaseUntil());
    }

    private void assertInstantCloseToMicros(Instant expected, Instant actual) {
        Duration delta = Duration.between(expected, actual).abs();
        assertThat(delta).isLessThanOrEqualTo(ChronoUnit.MICROS.getDuration());
    }
}
