/*
 * どこで: Notification NATS 購読テスト
 * 何を: MaxDeliver advisory の stream_seq 保存と ack/nak を検証する
 * なぜ: 未処理 DLQ の登録動作を最低限保証するため
 */
package com.example.notification.nats;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.notification.config.NotificationNatsAdvisoryProperties;
import com.example.notification.config.NotificationNatsProperties;
import com.example.notification.repository.NotificationNatsDlqRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Message;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationMaxDeliverAdvisorySubscriberTest {

  private static final String SUBJECT =
      "$JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.entitlement-events.notification-entitlement-consumer";
  private static final String STREAM = "notification-advisory-dlq";
  private static final String DURABLE = "notification-advisory-consumer";
  private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(2);
  private static final Duration ACK_WAIT = Duration.ofSeconds(10);
  private static final int MAX_DELIVER = 10;
  private static final long STREAM_SEQ = 42L;
  private static final Instant NOW = Instant.parse("2026-01-12T00:00:00Z");

  @Mock private Connection connection;

  @Mock private NotificationNatsDlqRepository dlqRepository;

  private NotificationMaxDeliverAdvisorySubscriber subscriber;

  @BeforeEach
  void setUp() {
    final NotificationNatsProperties natsProperties =
        new NotificationNatsProperties(
            "entitlement.events",
            "entitlement-events",
            "notification-entitlement-consumer",
            DUPLICATE_WINDOW,
            ACK_WAIT,
            MAX_DELIVER);
    final NotificationNatsAdvisoryProperties advisoryProperties =
        new NotificationNatsAdvisoryProperties(SUBJECT, STREAM, DURABLE);
    subscriber =
        new NotificationMaxDeliverAdvisorySubscriber(
            connection,
            natsProperties,
            advisoryProperties,
            dlqRepository,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void insertsStreamSeqAndAcks() {
    final Message message = mock(Message.class);
    final String payload =
        ("{%n"
                + "  \"stream\": \"entitlement-events\",%n"
                + "  \"consumer\": \"notification-entitlement-consumer\",%n"
                + "  \"stream_seq\": %d%n"
                + "}%n")
            .formatted(STREAM_SEQ);
    when(message.getData()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));

    subscriber.handleMessage(message);

    verify(dlqRepository).insert(STREAM_SEQ, NOW);
    verify(message).ack();
    verify(message, never()).nak();
  }

  @Test
  void ackWhenStreamSeqMissing() {
    final Message message = mock(Message.class);
    final String payload =
        """
        {
          "stream": "entitlement-events",
          "consumer": "notification-entitlement-consumer"
        }
        """;
    when(message.getData()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));

    subscriber.handleMessage(message);

    verifyNoInteractions(dlqRepository);
    verify(message).ack();
    verify(message, never()).nak();
  }
}
