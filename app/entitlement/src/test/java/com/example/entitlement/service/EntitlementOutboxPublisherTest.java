/*
 * どこで: Entitlement outbox publish のユニットテスト
 * 何を: outbox publish の puback/失敗/パース失敗の挙動を検証する
 * なぜ: puback 受信時のみ publish 成功とみなし、失敗と非リトライを正しく扱うため
 */
package com.example.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.common.event.EntitlementEventPayload;
import com.example.entitlement.config.EntitlementNatsProperties;
import com.example.entitlement.config.EntitlementOutboxProperties;
import com.example.entitlement.model.OutboxEventRecord;
import com.example.entitlement.model.OutboxStatus;
import com.example.entitlement.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntitlementOutboxPublisherTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-01-17T00:00:00Z");
  // application.yaml と同じ値で固定し、テストの揺れを防ぐ
  private static final EntitlementOutboxProperties PROPERTIES =
      new EntitlementOutboxProperties(
          true,
          Duration.ofSeconds(1),
          50,
          10,
          Duration.ofSeconds(1),
          Duration.ofSeconds(60),
          2.0d,
          0.5d,
          1.5d,
          Duration.ofSeconds(1),
          1000,
          Duration.ofSeconds(30),
          Duration.ofHours(24));
  private static final EntitlementNatsProperties NATS_PROPERTIES =
      new EntitlementNatsProperties(
          "entitlement.events", "entitlement-events", Duration.ofMinutes(2));
  private static final String INVALID_JSON = "{invalid_json";

  @Mock private JetStream jetStream;

  @Mock private OutboxEventRepository outboxEventRepository;

  @Mock private EntitlementMetrics metrics;

  private ObjectMapper objectMapper;
  private EntitlementOutboxPublisher publisher;

  @BeforeEach
  void setUp() {
    // 時刻に依存する処理が揺れないよう固定クロックを注入する
    final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    objectMapper = new ObjectMapper();
    publisher =
        new EntitlementOutboxPublisher(
            jetStream,
            outboxEventRepository,
            PROPERTIES,
            NATS_PROPERTIES,
            objectMapper,
            metrics,
            clock);
  }

  @Test
  void publishPendingBatchMovesToFailedWhenPayloadParseFails() {
    // パース不能な payload を持つ outbox レコードを用意する
    final OutboxEventRecord record =
        new OutboxEventRecord(
            UUID.randomUUID(), "EntitlementGranted", "user-1:sku-1", INVALID_JSON, 0);

    when(outboxEventRepository.claimPending(
            eq(PROPERTIES.batchSize()), eq(FIXED_NOW), any(Instant.class), anyString()))
        .thenReturn(List.of(record));
    // ロック喪失の警告を避けるために更新成功を返す
    when(outboxEventRepository.markFailure(
            eq(record.eventId()),
            anyString(),
            anyInt(),
            eq(OutboxStatus.FAILED),
            isNull(),
            anyString()))
        .thenReturn(1);

    publisher.publishPendingBatch();

    // 解析失敗時は publish も PUBLISHED 更新もしない
    verifyNoInteractions(jetStream);
    verify(outboxEventRepository, never())
        .markPublished(any(UUID.class), anyString(), any(Instant.class));

    // FAILED への即時遷移を検証する
    final ArgumentCaptor<String> lockedByCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<Integer> attemptCaptor = ArgumentCaptor.forClass(Integer.class);
    final ArgumentCaptor<Instant> nextRetryAtCaptor = ArgumentCaptor.forClass(Instant.class);
    final ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
    verify(outboxEventRepository)
        .markFailure(
            eq(record.eventId()),
            lockedByCaptor.capture(),
            attemptCaptor.capture(),
            eq(OutboxStatus.FAILED),
            nextRetryAtCaptor.capture(),
            errorCaptor.capture());

    assertThat(lockedByCaptor.getValue()).isNotBlank();
    assertThat(attemptCaptor.getValue()).isEqualTo(PROPERTIES.maxAttempts());
    assertThat(nextRetryAtCaptor.getValue()).isNull();
    assertThat(errorCaptor.getValue()).isEqualTo("outbox payload parse failure");
  }

  @Test
  void publishPendingBatchMarksPublishedWhenPubAckIsReceived()
      throws IOException, JetStreamApiException, JsonProcessingException {
    final UUID eventId = UUID.randomUUID();
    final OutboxEventRecord record = buildValidRecord(eventId);

    when(outboxEventRepository.claimPending(
            eq(PROPERTIES.batchSize()), eq(FIXED_NOW), any(Instant.class), anyString()))
        .thenReturn(List.of(record));
    when(jetStream.publish(eq(NATS_PROPERTIES.subject()), any(Headers.class), any(byte[].class)))
        .thenReturn(mock(PublishAck.class));
    when(outboxEventRepository.markPublished(eq(eventId), anyString(), eq(FIXED_NOW)))
        .thenReturn(1);

    publisher.publishPendingBatch();

    verify(jetStream).publish(eq(NATS_PROPERTIES.subject()), any(Headers.class), any(byte[].class));
    verify(outboxEventRepository).markPublished(eq(eventId), anyString(), eq(FIXED_NOW));
    verify(outboxEventRepository, never())
        .markFailure(
            any(UUID.class),
            anyString(),
            anyInt(),
            any(OutboxStatus.class),
            any(Instant.class),
            anyString());
  }

  @Test
  void publishPendingBatchMarksFailureWhenPubAckFails()
      throws IOException, JetStreamApiException, JsonProcessingException {
    final UUID eventId = UUID.randomUUID();
    final OutboxEventRecord record = buildValidRecord(eventId);

    when(outboxEventRepository.claimPending(
            eq(PROPERTIES.batchSize()), eq(FIXED_NOW), any(Instant.class), anyString()))
        .thenReturn(List.of(record));
    when(jetStream.publish(eq(NATS_PROPERTIES.subject()), any(Headers.class), any(byte[].class)))
        .thenThrow(new IOException("nats publish failed"));
    when(outboxEventRepository.markFailure(
            eq(eventId),
            anyString(),
            anyInt(),
            eq(OutboxStatus.PENDING),
            any(Instant.class),
            anyString()))
        .thenReturn(1);

    publisher.publishPendingBatch();

    verify(outboxEventRepository, never())
        .markPublished(any(UUID.class), anyString(), any(Instant.class));
    verify(outboxEventRepository)
        .markFailure(
            eq(eventId),
            anyString(),
            eq(1),
            eq(OutboxStatus.PENDING),
            any(Instant.class),
            anyString());
  }

  @Test
  void publishPendingBatchMarksFailureWhenPubAckIsNull()
      throws IOException, JetStreamApiException, JsonProcessingException {
    final UUID eventId = UUID.randomUUID();
    final OutboxEventRecord record = buildValidRecord(eventId);

    when(outboxEventRepository.claimPending(
            eq(PROPERTIES.batchSize()), eq(FIXED_NOW), any(Instant.class), anyString()))
        .thenReturn(List.of(record));
    when(jetStream.publish(eq(NATS_PROPERTIES.subject()), any(Headers.class), any(byte[].class)))
        .thenReturn(null);
    when(outboxEventRepository.markFailure(
            eq(eventId),
            anyString(),
            anyInt(),
            eq(OutboxStatus.PENDING),
            any(Instant.class),
            anyString()))
        .thenReturn(1);

    publisher.publishPendingBatch();

    verify(outboxEventRepository, never())
        .markPublished(any(UUID.class), anyString(), any(Instant.class));
    verify(outboxEventRepository)
        .markFailure(
            eq(eventId),
            anyString(),
            eq(1),
            eq(OutboxStatus.PENDING),
            any(Instant.class),
            eq("puback is missing"));
  }

  @Test
  void publishPendingBatchMarksFailureWhenJetStreamApiExceptionOccurs()
      throws IOException, JetStreamApiException, JsonProcessingException {
    final UUID eventId = UUID.randomUUID();
    final OutboxEventRecord record = buildValidRecord(eventId);
    final JetStreamApiException apiException = mock(JetStreamApiException.class);
    when(apiException.getMessage()).thenReturn("nats api error");

    when(outboxEventRepository.claimPending(
            eq(PROPERTIES.batchSize()), eq(FIXED_NOW), any(Instant.class), anyString()))
        .thenReturn(List.of(record));
    when(jetStream.publish(eq(NATS_PROPERTIES.subject()), any(Headers.class), any(byte[].class)))
        .thenThrow(apiException);
    when(outboxEventRepository.markFailure(
            eq(eventId),
            anyString(),
            anyInt(),
            eq(OutboxStatus.PENDING),
            any(Instant.class),
            anyString()))
        .thenReturn(1);

    publisher.publishPendingBatch();

    verify(outboxEventRepository, never())
        .markPublished(any(UUID.class), anyString(), any(Instant.class));
    verify(outboxEventRepository)
        .markFailure(
            eq(eventId),
            anyString(),
            eq(1),
            eq(OutboxStatus.PENDING),
            any(Instant.class),
            eq("nats api error"));
  }

  @Test
  void publishPendingBatchTruncatesErrorMessage()
      throws IOException, JetStreamApiException, JsonProcessingException {
    final UUID eventId = UUID.randomUUID();
    final OutboxEventRecord record = buildValidRecord(eventId);
    final String longMessage = "x".repeat(PROPERTIES.errorMessageMaxLength() + 5);

    when(outboxEventRepository.claimPending(
            eq(PROPERTIES.batchSize()), eq(FIXED_NOW), any(Instant.class), anyString()))
        .thenReturn(List.of(record));
    when(jetStream.publish(eq(NATS_PROPERTIES.subject()), any(Headers.class), any(byte[].class)))
        .thenThrow(new IOException(longMessage));
    when(outboxEventRepository.markFailure(
            eq(eventId),
            anyString(),
            anyInt(),
            eq(OutboxStatus.PENDING),
            any(Instant.class),
            anyString()))
        .thenReturn(1);

    publisher.publishPendingBatch();

    final ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
    verify(outboxEventRepository)
        .markFailure(
            eq(eventId),
            anyString(),
            eq(1),
            eq(OutboxStatus.PENDING),
            any(Instant.class),
            errorCaptor.capture());
    assertThat(errorCaptor.getValue()).hasSize(PROPERTIES.errorMessageMaxLength());
    assertThat(errorCaptor.getValue())
        .isEqualTo(longMessage.substring(0, PROPERTIES.errorMessageMaxLength()));
  }

  @Test
  void publishPendingBatchContinuesWhenMarkPublishedReturnsZero()
      throws IOException, JetStreamApiException, JsonProcessingException {
    final UUID firstId = UUID.randomUUID();
    final UUID secondId = UUID.randomUUID();
    final OutboxEventRecord first = buildValidRecord(firstId);
    final OutboxEventRecord second = buildValidRecord(secondId);

    when(outboxEventRepository.claimPending(
            eq(PROPERTIES.batchSize()), eq(FIXED_NOW), any(Instant.class), anyString()))
        .thenReturn(List.of(first, second));
    when(jetStream.publish(eq(NATS_PROPERTIES.subject()), any(Headers.class), any(byte[].class)))
        .thenReturn(mock(PublishAck.class));
    when(outboxEventRepository.markPublished(any(UUID.class), anyString(), eq(FIXED_NOW)))
        .thenReturn(0, 1);

    publisher.publishPendingBatch();

    verify(jetStream, times(2))
        .publish(eq(NATS_PROPERTIES.subject()), any(Headers.class), any(byte[].class));
    verify(outboxEventRepository, times(2))
        .markPublished(any(UUID.class), anyString(), eq(FIXED_NOW));
    verify(outboxEventRepository, never())
        .markFailure(
            any(UUID.class),
            anyString(),
            anyInt(),
            any(OutboxStatus.class),
            any(Instant.class),
            anyString());
  }

  private OutboxEventRecord buildValidRecord(UUID eventId) throws JsonProcessingException {
    // snake_case 変換を含めた payload 生成を ObjectMapper に任せる
    final EntitlementEventPayload payload =
        new EntitlementEventPayload(
            eventId.toString(),
            "EntitlementGranted",
            FIXED_NOW.toString(),
            "user-1",
            "sku-1",
            "entitlement-service",
            "source-1",
            1L,
            "trace-1");
    final String payloadJson = objectMapper.writeValueAsString(payload);
    return new OutboxEventRecord(eventId, "EntitlementGranted", "user-1:sku-1", payloadJson, 0);
  }
}
