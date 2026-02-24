package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.ProcessedEventRepository;
import com.example.proto.matchmaking.MatchmakingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchmakingEventHandlerTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-02-24T12:00:00Z");
  private static final String EVENT_ID = "22222222-2222-2222-2222-222222222222";
  private static final String MATCH_ID = "match-1";
  private static final String OCCURRED_AT = "2026-02-24T11:59:30Z";
  private static final String TRACE_ID = "trace-1";
  private static final String PAYLOAD_JSON = "{\"match_id\":\"match-1\"}";

  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private NotificationRepository notificationRepository;
  @Mock private ObjectMapper objectMapper;
  @Captor private ArgumentCaptor<NotificationRecord> recordCaptor;
  @Captor private ArgumentCaptor<Map<String, String>> payloadCaptor;

  private MatchmakingEventHandler handler;

  @BeforeEach
  void setUp() {
    final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    handler =
        new MatchmakingEventHandler(
            processedEventRepository, notificationRepository, objectMapper, clock);
  }

  @Test
  void skipInsertWhenEventAlreadyProcessed() throws JsonProcessingException {
    final MatchmakingEvent event = buildEvent();
    when(processedEventRepository.insertIfAbsent(UUID.fromString(EVENT_ID), FIXED_NOW))
        .thenReturn(true, false);
    doReturn(PAYLOAD_JSON).when(objectMapper).writeValueAsString(any(Map.class));

    handler.handleMatchmakingEvent(event);
    handler.handleMatchmakingEvent(event);

    verify(notificationRepository, times(1)).insert(any(NotificationRecord.class));
  }

  @Test
  void insertNotificationWithMatchIdPayloadOnly() throws JsonProcessingException {
    final MatchmakingEvent event = buildEvent();
    when(processedEventRepository.insertIfAbsent(UUID.fromString(EVENT_ID), FIXED_NOW))
        .thenReturn(true);
    doReturn(PAYLOAD_JSON).when(objectMapper).writeValueAsString(any(Map.class));

    handler.handleMatchmakingEvent(event);

    verify(notificationRepository).insert(recordCaptor.capture());
    verify(objectMapper).writeValueAsString(payloadCaptor.capture());

    final NotificationRecord record = recordCaptor.getValue();
    final Map<String, String> payload = payloadCaptor.getValue();

    assertThat(payload).containsOnlyKeys("match_id");
    assertThat(payload.get("match_id")).isEqualTo(MATCH_ID);
    assertThat(record.eventId()).isEqualTo(UUID.fromString(EVENT_ID));
    assertThat(record.type()).isEqualTo("MatchFound");
    assertThat(record.status()).isEqualTo(NotificationStatus.PENDING);
    assertThat(record.payloadJson()).isEqualTo(PAYLOAD_JSON);
    assertThat(record.occurredAt()).isEqualTo(Instant.parse(OCCURRED_AT));
    assertThat(record.nextRetryAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void throwPermanentExceptionWhenEventIdInvalid() {
    final MatchmakingEvent event = buildEvent().toBuilder().setEventId("bad-id").build();

    assertThatThrownBy(() -> handler.handleMatchmakingEvent(event))
        .isInstanceOf(NotificationEventPermanentException.class)
        .hasMessageContaining("invalid matchmaking event_id");

    verify(notificationRepository, never()).insert(any(NotificationRecord.class));
  }

  @Test
  void throwPermanentExceptionWhenOccurredAtInvalid() {
    final MatchmakingEvent event = buildEvent().toBuilder().setOccurredAt("bad-time").build();

    assertThatThrownBy(() -> handler.handleMatchmakingEvent(event))
        .isInstanceOf(NotificationEventPermanentException.class)
        .hasMessageContaining("invalid matchmaking occurred_at");
  }

  @Test
  void throwPermanentExceptionWhenPayloadSerializationFails() throws JsonProcessingException {
    final MatchmakingEvent event = buildEvent();
    when(processedEventRepository.insertIfAbsent(UUID.fromString(EVENT_ID), FIXED_NOW))
        .thenReturn(true);
    when(objectMapper.writeValueAsString(any(Map.class)))
        .thenThrow(new TestJsonProcessingException());

    assertThatThrownBy(() -> handler.handleMatchmakingEvent(event))
        .isInstanceOf(NotificationEventPermanentException.class)
        .hasMessageContaining("matchmaking payload serialization failure");
  }

  private MatchmakingEvent buildEvent() {
    return MatchmakingEvent.newBuilder()
        .setEventId(EVENT_ID)
        .setEventType(MatchmakingEvent.EventType.MATCH_FOUND)
        .setOccurredAt(OCCURRED_AT)
        .setMatchId(MATCH_ID)
        .setMode("casual")
        .setTraceId(TRACE_ID)
        .build();
  }

  private static final class TestJsonProcessingException extends JsonProcessingException {
    private TestJsonProcessingException() {
      super("boom");
    }
  }
}
