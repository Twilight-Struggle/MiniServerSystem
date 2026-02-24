/*
 * どこで: Entitlement outbox publish サービス
 * 何を: outbox_events を claim して NATS へ publish する
 * なぜ: DB 更新とイベント配信の整合性を保つため
 */
package com.example.entitlement.service;

import com.example.common.event.EntitlementEventPayload;
import com.example.entitlement.config.EntitlementNatsProperties;
import com.example.entitlement.config.EntitlementOutboxProperties;
import com.example.entitlement.model.OutboxEventRecord;
import com.example.entitlement.model.OutboxStatus;
import com.example.entitlement.repository.OutboxEventRepository;
import com.example.proto.entitlement.EntitlementEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "entitlement.outbox.enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EntitlementOutboxPublisher {

  private static final Logger logger = LoggerFactory.getLogger(EntitlementOutboxPublisher.class);
  private static final String HOSTNAME_ENV = "HOSTNAME";
  private static final String DEFAULT_HOSTNAME = "unknown-host";
  private static final String HEADER_MESSAGE_ID = "Nats-Msg-Id";
  private static final String HEADER_EVENT_TYPE = "event_type";
  private static final String HEADER_AGGREGATE_KEY = "aggregate_key";
  private static final String HEADER_OCCURRED_AT = "occurred_at";
  private static final String HEADER_TRACE_ID = "trace_id";

  private final JetStream jetStream;
  private final OutboxEventRepository outboxEventRepository;
  private final EntitlementOutboxProperties properties;
  private final EntitlementNatsProperties natsProperties;
  private final ObjectMapper objectMapper;
  private final EntitlementMetrics metrics;
  private final Clock clock;

  public void publishPendingBatch() {
    final Instant now = Instant.now(clock);
    final String lockedBy = resolveLockedBy();
    final Instant leaseUntil = now.plus(properties.lease());
    final List<OutboxEventRecord> pending =
        outboxEventRepository.claimPending(properties.batchSize(), now, leaseUntil, lockedBy);
    for (OutboxEventRecord record : pending) {
      try {
        final EntitlementEventPayload payload = parsePayload(record);
        metrics.recordOutboxBacklogAge(Instant.parse(payload.occurredAt()), now);
        final EntitlementEvent event = buildEvent(record, payload);
        final Headers headers = buildHeaders(record, payload);
        final PublishAck ack = publishWithAck(headers, event);
        if (ack == null) {
          throw new IllegalStateException("puback is missing");
        }
        final int updated = outboxEventRepository.markPublished(record.eventId(), lockedBy, now);
        if (updated == 0) {
          logger.warn("outbox publish succeeded but lock was lost eventId={}", record.eventId());
        } else {
          metrics.recordOutboxPublishDelay(Instant.parse(payload.occurredAt()), now);
        }
      } catch (JetStreamApiException | IOException ex) {
        handleFailure(record, ex, now, lockedBy);
      } catch (DataAccessException ex) {
        handleFailure(record, ex, now, lockedBy);
      } catch (RuntimeException ex) {
        handleFailure(record, ex, now, lockedBy);
      }
    }
    metrics.updateOutboxFailedCurrent(outboxEventRepository.countFailed());
  }

  private EntitlementEventPayload parsePayload(OutboxEventRecord record) {
    try {
      return objectMapper.readValue(record.payloadJson(), EntitlementEventPayload.class);
    } catch (JsonProcessingException ex) {
      // パース不能はリトライしても回復しない前提なので専用例外で即時FAILEDに寄せる
      throw new OutboxPayloadParseException("outbox payload parse failure", ex);
    }
  }

  private EntitlementEvent buildEvent(OutboxEventRecord record, EntitlementEventPayload payload) {
    final EntitlementEvent.EventType eventType = mapEventType(record.eventType());
    return EntitlementEvent.newBuilder()
        .setEventId(payload.eventId())
        .setEventType(eventType)
        .setOccurredAt(payload.occurredAt())
        .setUserId(payload.userId())
        .setStockKeepingUnit(payload.stockKeepingUnit())
        .setSource(payload.source())
        .setSourceId(payload.sourceId())
        .setVersion(payload.version())
        .setTraceId(payload.traceId())
        .build();
  }

  private Headers buildHeaders(OutboxEventRecord record, EntitlementEventPayload payload) {
    final Headers headers = new Headers();
    // 重複排除キーとして event_id を NATS の標準ヘッダに載せる
    headers.add(HEADER_MESSAGE_ID, payload.eventId());
    headers.add(HEADER_EVENT_TYPE, record.eventType());
    headers.add(HEADER_AGGREGATE_KEY, record.aggregateKey());
    headers.add(HEADER_OCCURRED_AT, payload.occurredAt());
    headers.add(HEADER_TRACE_ID, payload.traceId());
    return headers;
  }

  private PublishAck publishWithAck(Headers headers, EntitlementEvent event)
      throws IOException, JetStreamApiException {
    // puback を受け取れた場合のみ publish 成功とみなす
    return jetStream.publish(natsProperties.subject(), headers, event.toByteArray());
  }

  private void handleFailure(OutboxEventRecord record, Exception ex, Instant now, String lockedBy) {
    final boolean nonRetryable = ex instanceof OutboxPayloadParseException;
    final int nextAttempt = nonRetryable ? properties.maxAttempts() : record.attemptCount() + 1;
    final boolean failed = nonRetryable || nextAttempt >= properties.maxAttempts();
    final Instant nextRetryAt = failed ? null : now.plus(computeBackoffDuration(nextAttempt));
    final int updated =
        outboxEventRepository.markFailure(
            record.eventId(),
            lockedBy,
            nextAttempt,
            failed ? OutboxStatus.FAILED : OutboxStatus.PENDING,
            nextRetryAt,
            truncateError(ex.getMessage()));
    if (updated == 0) {
      logger.warn(
          "outbox retry skipped because lock was lost eventId={} attempt={}",
          record.eventId(),
          nextAttempt);
    }
    if (failed) {
      if (nonRetryable) {
        // 運用アラート向けに error レベルで即時 FAILED を通知する
        logger.error(
            "outbox payload parse failed and moved to FAILED eventId={}", record.eventId(), ex);
      } else {
        logger.warn("outbox publish moved to FAILED eventId={}", record.eventId(), ex);
      }
    } else {
      logger.warn(
          "outbox publish retry scheduled eventId={} attempt={}",
          record.eventId(),
          nextAttempt,
          ex);
    }
  }

  private Duration computeBackoffDuration(int attempt) {
    final double baseMillis = properties.backoffBase().toMillis();
    final double exp = baseMillis * Math.pow(properties.backoffExponentBase(), (attempt - 1));
    final double capped = Math.min(exp, properties.backoffMax().toMillis());
    final double jitterMin = properties.backoffJitterMin();
    final double jitterMax = properties.backoffJitterMax();
    final double jitter =
        jitterMin + ThreadLocalRandom.current().nextDouble() * (jitterMax - jitterMin);
    final long backoffMillis = (long) Math.ceil(capped * jitter);
    final long minMillis = properties.backoffMin().toMillis();
    return Duration.ofMillis(Math.max(minMillis, backoffMillis));
  }

  private String truncateError(String message) {
    if (message == null) {
      return "unknown error";
    }
    final int maxLength = properties.errorMessageMaxLength();
    if (message.length() <= maxLength) {
      return message;
    }
    return message.substring(0, maxLength);
  }

  private EntitlementEvent.EventType mapEventType(String eventType) {
    return switch (eventType) {
      case "EntitlementGranted" -> EntitlementEvent.EventType.ENTITLEMENT_GRANTED;
      case "EntitlementRevoked" -> EntitlementEvent.EventType.ENTITLEMENT_REVOKED;
      default -> throw new IllegalArgumentException("unknown event type: " + eventType);
    };
  }

  private String resolveLockedBy() {
    final String env = System.getenv(HOSTNAME_ENV);
    if (env != null && !env.isBlank()) {
      return env;
    }
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException | SecurityException ex) {
      logger.warn("failed to resolve hostname; fallback to {}", DEFAULT_HOSTNAME, ex);
      return DEFAULT_HOSTNAME;
    }
  }

  private static final class OutboxPayloadParseException extends RuntimeException {
    private OutboxPayloadParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
