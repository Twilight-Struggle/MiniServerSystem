/*
 * どこで: NotificationEventHandler のユニットテスト
 * 何を: 冪等性・通知レコード生成・例外伝播を検証する
 * なぜ: 重複登録や不正な保存を防ぐため
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
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {

        private static final Instant FIXED_NOW = Instant.parse("2026-01-17T01:02:03Z");
        private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";
        private static final String USER_ID = "user-1";
        private static final String SKU = "sku-1";
        private static final String SOURCE = "entitlement";
        private static final String SOURCE_ID = "source-1";
        private static final long EVENT_VERSION = 1L;
        private static final String TRACE_ID = "trace-1";
        private static final String OCCURRED_AT = "2026-01-12T00:00:00Z";
        private static final String PAYLOAD_JSON = "{\"event_id\":\"11111111-1111-1111-1111-111111111111\",\"occurred_at\":\"2026-01-12T00:00:00Z\"}";

        @Mock
        private ProcessedEventRepository processedEventRepository;

        @Mock
        private NotificationRepository notificationRepository;

        @Mock
        private ObjectMapper objectMapper;

        @Captor
        private ArgumentCaptor<NotificationRecord> recordCaptor;

        @Captor
        private ArgumentCaptor<EntitlementEventPayload> payloadCaptor;

        private NotificationEventHandler handler;

        @BeforeEach
        void setUp() {
                // 時刻に依存する処理が揺れないよう固定クロックを注入する
                Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
                handler = new NotificationEventHandler(
                                processedEventRepository,
                                notificationRepository,
                                objectMapper,
                                clock);
        }

        @Test
        void skipInsertWhenSameEventHandledTwice() throws Exception {
                // 同一 event_id を 2 回処理すると 2 回目は insert されないことを検証する
                EntitlementEvent event = buildEvent(EntitlementEvent.EventType.ENTITLEMENT_GRANTED);
                UUID eventId = UUID.fromString(EVENT_ID);
                when(processedEventRepository.insertIfAbsent(eventId, FIXED_NOW))
                                .thenReturn(true, false);
                when(objectMapper.writeValueAsString(any(EntitlementEventPayload.class)))
                                .thenReturn(PAYLOAD_JSON);

                handler.handleEntitlementEvent(event);
                handler.handleEntitlementEvent(event);

                // processed_events は 2 回試行され、通知登録は 1 回だけになる
                verify(processedEventRepository, times(2)).insertIfAbsent(eventId, FIXED_NOW);
                verify(notificationRepository, times(1)).insert(any(NotificationRecord.class));
                verify(objectMapper, times(1)).writeValueAsString(any(EntitlementEventPayload.class));
        }

        @Test
        void insertNotificationRecordWhenFirstSeen() throws Exception {
                // 初回のイベントでは通知レコードが正しく生成されることを検証する
                EntitlementEvent event = buildEvent(EntitlementEvent.EventType.ENTITLEMENT_GRANTED);
                UUID eventId = UUID.fromString(EVENT_ID);
                when(processedEventRepository.insertIfAbsent(eventId, FIXED_NOW))
                                .thenReturn(true);
                when(objectMapper.writeValueAsString(any(EntitlementEventPayload.class)))
                                .thenReturn(PAYLOAD_JSON);

                handler.handleEntitlementEvent(event);

                verify(notificationRepository).insert(recordCaptor.capture());
                verify(objectMapper).writeValueAsString(payloadCaptor.capture());

                NotificationRecord record = recordCaptor.getValue();
                EntitlementEventPayload payload = payloadCaptor.getValue();

                // payload 側のマッピングとデータコピーを確認する
                assertThat(payload.eventId()).isEqualTo(EVENT_ID);
                assertThat(payload.eventType()).isEqualTo("EntitlementGranted");
                assertThat(payload.occurredAt()).isEqualTo(OCCURRED_AT);
                assertThat(payload.userId()).isEqualTo(USER_ID);
                assertThat(payload.stockKeepingUnit()).isEqualTo(SKU);
                assertThat(payload.source()).isEqualTo(SOURCE);
                assertThat(payload.sourceId()).isEqualTo(SOURCE_ID);
                assertThat(payload.version()).isEqualTo(EVENT_VERSION);
                assertThat(payload.traceId()).isEqualTo(TRACE_ID);

                // notifications への登録内容が期待通りであることを確認する
                assertThat(record.eventId()).isEqualTo(eventId);
                assertThat(record.userId()).isEqualTo(USER_ID);
                assertThat(record.type()).isEqualTo("EntitlementGranted");
                assertThat(record.occurredAt()).isEqualTo(Instant.parse(OCCURRED_AT));
                assertThat(record.payloadJson()).isEqualTo(PAYLOAD_JSON);
                assertThat(record.status()).isEqualTo(NotificationStatus.PENDING);
                assertThat(record.nextRetryAt()).isEqualTo(FIXED_NOW);
                assertThat(record.createdAt()).isEqualTo(FIXED_NOW);
                assertThat(record.notificationId()).isNotNull();
        }

        @Test
        void throwPermanentExceptionWhenPayloadSerializationFails() throws Exception {
                // payload 生成に失敗した場合は恒久的例外を送出することを検証する
                EntitlementEvent event = buildEvent(EntitlementEvent.EventType.ENTITLEMENT_GRANTED);
                UUID eventId = UUID.fromString(EVENT_ID);
                JsonProcessingException failure = new JsonProcessingException("boom") {
                };
                when(processedEventRepository.insertIfAbsent(eventId, FIXED_NOW))
                                .thenReturn(true);
                doThrow(failure)
                                .when(objectMapper)
                                .writeValueAsString(any(EntitlementEventPayload.class));

                assertThatThrownBy(() -> handler.handleEntitlementEvent(event))
                                .isInstanceOf(NotificationEventPermanentException.class)
                                .hasMessageContaining("notification payload serialization failure")
                                .hasCause(failure);

                // 例外時は通知登録されないことを保証する
                verify(notificationRepository, never()).insert(any(NotificationRecord.class));
        }

        private EntitlementEvent buildEvent(EntitlementEvent.EventType eventType) {
                // テスト用のイベントを固定値で構築し、揺れない入力を提供する
                return EntitlementEvent.newBuilder()
                                .setEventId(EVENT_ID)
                                .setEventType(eventType)
                                .setOccurredAt(OCCURRED_AT)
                                .setUserId(USER_ID)
                                .setStockKeepingUnit(SKU)
                                .setSource(SOURCE)
                                .setSourceId(SOURCE_ID)
                                .setVersion(EVENT_VERSION)
                                .setTraceId(TRACE_ID)
                                .build();
        }
}
