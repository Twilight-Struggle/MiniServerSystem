package com.example.notification.service;

import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.ProcessedEventRepository;
import com.example.proto.matchmaking.MatchmakingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchmakingEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(MatchmakingEventHandler.class);
  private static final String NOTIFICATION_TYPE_MATCH_FOUND = "MatchFound";
  private static final String SYSTEM_USER_ID = "matchmaking-system";

  private final ProcessedEventRepository processedEventRepository;
  private final NotificationRepository notificationRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Transactional
  public void handleMatchmakingEvent(MatchmakingEvent event) {
    final UUID eventId = parseEventId(event);
    final Instant occurredAt = parseOccurredAt(event);
    final Instant now = Instant.now(clock);
    final boolean inserted = processedEventRepository.insertIfAbsent(eventId, now);
    if (!inserted) {
      logger.info("duplicate matchmaking event skipped");
      return;
    }
    final String payloadJson = serializePayload(event.getMatchId());
    final NotificationRecord record =
        new NotificationRecord(
            UUID.randomUUID(),
            eventId,
            SYSTEM_USER_ID,
            NOTIFICATION_TYPE_MATCH_FOUND,
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
    logger.info("matchmaking event accepted and queued");
  }

  private UUID parseEventId(MatchmakingEvent event) {
    try {
      return UUID.fromString(event.getEventId());
    } catch (RuntimeException ex) {
      throw new NotificationEventPermanentException("invalid matchmaking event_id", ex);
    }
  }

  private Instant parseOccurredAt(MatchmakingEvent event) {
    try {
      return Instant.parse(event.getOccurredAt());
    } catch (RuntimeException ex) {
      throw new NotificationEventPermanentException("invalid matchmaking occurred_at", ex);
    }
  }

  private String serializePayload(String matchId) {
    try {
      return objectMapper.writeValueAsString(Map.of("match_id", matchId));
    } catch (JsonProcessingException ex) {
      throw new NotificationEventPermanentException(
          "matchmaking payload serialization failure", ex);
    }
  }
}
