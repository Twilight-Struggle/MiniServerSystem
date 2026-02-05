/*
 * どこで: Notification テスト
 * 何を: 失敗時に DLQ へ移送されることを検証する
 * なぜ: 完全失敗の隔離が機能することを担保するため
 */
package com.example.notification.service;

import static com.example.common.JdbcTimestampUtils.toTimestamp;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.notification.AbstractPostgresContainerTest;
import com.example.notification.config.NotificationDeliveryProperties;
import com.example.notification.model.NotificationRecord;
import com.example.notification.repository.NotificationDlqRepository;
import com.example.notification.repository.NotificationRepository;
import com.example.proto.entitlement.EntitlementEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"notification.delivery.max-attempts=1"})
@ActiveProfiles("test")
class NotificationDlqTest extends AbstractPostgresContainerTest {

  @Autowired private NotificationEventHandler eventHandler;

  @Autowired private NotificationDeliveryService deliveryService;

  @Autowired private NotificationDlqRepository dlqRepository;

  @Autowired private NotificationRepository notificationRepository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private NotificationDeliveryProperties deliveryProperties;

  @Test
  void failedDeliveryMovesToDlq() {
    final String eventId = UUID.randomUUID().toString();
    final EntitlementEvent event =
        EntitlementEvent.newBuilder()
            .setEventId(eventId)
            .setEventType(EntitlementEvent.EventType.ENTITLEMENT_GRANTED)
            .setOccurredAt(Instant.now().toString())
            .setUserId("u_1")
            .setStockKeepingUnit("item_1")
            .setSource("purchase")
            .setSourceId("p_1")
            .setVersion(1L)
            .setTraceId("trace-1")
            .build();

    eventHandler.handleEntitlementEvent(event);
    deliveryService.processPendingBatch();

    assertThat(dlqRepository.countByEventId(UUID.fromString(eventId))).isEqualTo(1);
  }

  @Test
  void dlqInsertIsRolledBackWhenLockIsLost() {
    final String eventId = UUID.randomUUID().toString();
    final EntitlementEvent event =
        EntitlementEvent.newBuilder()
            .setEventId(eventId)
            .setEventType(EntitlementEvent.EventType.ENTITLEMENT_GRANTED)
            .setOccurredAt(Instant.now().toString())
            .setUserId("u_1")
            .setStockKeepingUnit("item_1")
            .setSource("purchase")
            .setSourceId("p_1")
            .setVersion(1L)
            .setTraceId("trace-1")
            .build();

    eventHandler.handleEntitlementEvent(event);
    final NotificationRecord record = notificationRepository.findByUserId("u_1").get(0);

    final Instant now = Instant.now();
    final String sql =
        """
                UPDATE notifications
                SET status = 'PROCESSING',
                    locked_by = :lockedBy,
                    locked_at = :now,
                    lease_until = :leaseUntil
                WHERE notification_id = :notificationId
                """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("notificationId", record.notificationId())
            .addValue("lockedBy", "other-worker")
            .addValue("now", toTimestamp(now))
            .addValue("leaseUntil", toTimestamp(now.plus(deliveryProperties.lease())));
    jdbcTemplate.update(sql, params);

    deliveryService.handleFailure(
        record, new IllegalStateException("simulated failure"), now, "worker-a");

    assertThat(dlqRepository.countByEventId(UUID.fromString(eventId))).isZero();
  }

  @TestConfiguration
  static class FailingSenderConfig {

    @Bean
    @Primary
    NotificationSender failingSender() {
      return record -> {
        throw new IllegalStateException("simulated failure");
      };
    }
  }
}
