/*
 * どこで: Notification サービス層
 * 何を: NATS 受信イベントの永続化と冪等性処理を行う
 * なぜ: at-least-once 配信で重複登録を避けるため
 */
package com.example.notification.service;

import com.example.common.event.EntitlementEventPayload;
import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.ProcessedEventRepository;
import com.example.proto.entitlement.EntitlementEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationEventHandler {

  private final ProcessedEventRepository processedEventRepository;
  private final NotificationRepository notificationRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Transactional
  public void handleEntitlementEvent(EntitlementEvent event) {
    final UUID eventId = parseEventId(event);
    final Instant occurredAt = parseOccurredAt(event);
    final Instant now = Instant.now(clock);
    // processed_events に先行登録して重複処理を抑止する
    final boolean inserted = processedEventRepository.insertIfAbsent(eventId, now);
    if (!inserted) {
      return;
    }
    final EntitlementEventPayload payload =
        new EntitlementEventPayload(
            event.getEventId(),
            mapEventType(event),
            event.getOccurredAt(),
            event.getUserId(),
            event.getStockKeepingUnit(),
            event.getSource(),
            event.getSourceId(),
            event.getVersion(),
            event.getTraceId());
    final String payloadJson = serializePayload(payload);
    final NotificationRecord record =
        new NotificationRecord(
            UUID.randomUUID(),
            eventId,
            event.getUserId(),
            mapEventType(event),
            occurredAt,
            payloadJson,
            NotificationStatus.PENDING,
            null,
            null,
            null,
            0,
            now,
            now,
            null);
    notificationRepository.insert(record);
  }

  private String mapEventType(EntitlementEvent event) {
    return switch (event.getEventType()) {
      case ENTITLEMENT_GRANTED -> "EntitlementGranted";
      case ENTITLEMENT_REVOKED -> "EntitlementRevoked";
      case UNRECOGNIZED -> "Unrecognized";
    };
  }

  private UUID parseEventId(EntitlementEvent event) {
    try {
      return UUID.fromString(event.getEventId());
    } catch (RuntimeException ex) {
      // event_id 不正は再配信しても回復しないため恒久的に扱う
      throw new NotificationEventPermanentException("invalid entitlement event_id", ex);
    }
  }

  private Instant parseOccurredAt(EntitlementEvent event) {
    try {
      return Instant.parse(event.getOccurredAt());
    } catch (RuntimeException ex) {
      // occurred_at 不正は再配信しても回復しないため恒久的に扱う
      throw new NotificationEventPermanentException("invalid entitlement occurred_at", ex);
    }
  }

  private String serializePayload(EntitlementEventPayload payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      // シリアライズ失敗は実装バグのため恒久的に扱う
      throw new NotificationEventPermanentException(
          "notification payload serialization failure", ex);
    }
  }
}
