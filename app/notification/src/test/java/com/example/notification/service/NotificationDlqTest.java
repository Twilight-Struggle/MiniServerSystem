/*
 * どこで: Notification テスト
 * 何を: 失敗時に DLQ へ移送されることを検証する
 * なぜ: 完全失敗の隔離が機能することを担保するため
 */
package com.example.notification.service;

import com.example.notification.AbstractPostgresContainerTest;
import com.example.notification.repository.NotificationDlqRepository;
import com.example.proto.entitlement.EntitlementEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "notification.delivery.max-attempts=1"
})
@ActiveProfiles("test")
class NotificationDlqTest extends AbstractPostgresContainerTest {

    @Autowired
    private NotificationEventHandler eventHandler;

    @Autowired
    private NotificationDeliveryService deliveryService;

    @Autowired
    private NotificationDlqRepository dlqRepository;

    @Test
    void failedDeliveryMovesToDlq() {
        String eventId = UUID.randomUUID().toString();
        EntitlementEvent event = EntitlementEvent.newBuilder()
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
