/*
 * どこで: Notification サービス層
 * 何を: NATS 受信イベントの永続化と冪等性処理を行う
 * なぜ: at-least-once 配信で重複登録を避けるため
 */
package com.example.notification.service;

import com.example.notification.model.NotificationFromEntitlementPayload;
import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.ProcessedEventRepository;
import com.example.proto.entitlement.EntitlementEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
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
        UUID eventId = UUID.fromString(event.getEventId());
        Instant now = Instant.now(clock);
        // processed_events に先行登録して重複処理を抑止する
        boolean inserted = processedEventRepository.insertIfAbsent(eventId, now);
        if (!inserted) {
            return;
        }
        NotificationFromEntitlementPayload payload = new NotificationFromEntitlementPayload(
                event.getEventId(),
                mapEventType(event),
                event.getUserId(),
                event.getStockKeepingUnit(),
                event.getSource(),
                event.getSourceId(),
                event.getVersion(),
                event.getTraceId());
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            NotificationRecord record = new NotificationRecord(
                    UUID.randomUUID(),
                    eventId,
                    event.getUserId(),
                    mapEventType(event),
                    Instant.parse(event.getOccurredAt()),
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
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("notification payload serialization failure", ex);
        }
    }

    private String mapEventType(EntitlementEvent event) {
        return switch (event.getEventType()) {
            case ENTITLEMENT_GRANTED -> "EntitlementGranted";
            case ENTITLEMENT_REVOKED -> "EntitlementRevoked";
            case UNRECOGNIZED -> "Unrecognized";
        };
    }
}
