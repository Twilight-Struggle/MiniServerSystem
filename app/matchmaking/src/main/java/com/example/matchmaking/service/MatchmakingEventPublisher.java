package com.example.matchmaking.service;

import com.example.matchmaking.config.MatchmakingNatsProperties;
import com.example.matchmaking.model.MatchPair;
import com.example.proto.matchmaking.MatchmakingEvent;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.impl.Headers;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true", matchIfMissing = true)
public class MatchmakingEventPublisher {

  private final JetStream jetStream;
  private final MatchmakingNatsProperties properties;
  private final Clock clock;

  public MatchmakingEventPublisher(
      JetStream jetStream, MatchmakingNatsProperties properties, Clock clock) {
    this.jetStream = jetStream;
    this.properties = properties;
    this.clock = clock;
  }

  public void publishMatched(MatchPair pair) {
    if (pair == null || pair.matchId() == null || pair.matchId().isBlank()) {
      throw new IllegalArgumentException("matchId is required");
    }
    final String eventId = UUID.randomUUID().toString();
    final MatchmakingEvent event =
        MatchmakingEvent.newBuilder()
            .setEventId(eventId)
            .setEventType(MatchmakingEvent.EventType.MATCH_FOUND)
            .setOccurredAt(Instant.now(clock).toString())
            .setMatchId(pair.matchId())
            .setMode(pair.mode().value())
            .setTraceId(resolveTraceId())
            .build();
    final Headers headers = new Headers();
    headers.add("Nats-Msg-Id", eventId);
    try {
      jetStream.publish(properties.subject(), headers, event.toByteArray());
    } catch (IOException | JetStreamApiException ex) {
      throw new IllegalStateException("failed to publish matchmaking event", ex);
    }
  }

  private String resolveTraceId() {
    final String traceId = MDC.get("trace_id");
    if (traceId != null && !traceId.isBlank()) {
      return traceId;
    }
    final String legacyTraceId = MDC.get("traceId");
    if (legacyTraceId != null && !legacyTraceId.isBlank()) {
      return legacyTraceId;
    }
    return UUID.randomUUID().toString();
  }
}
