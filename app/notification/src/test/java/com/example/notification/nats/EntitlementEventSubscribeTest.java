/*
 * どこで: Notification NATS JetStream 購読テスト(統合寄り)
 * 何を: start() 経由で handleMessage が配線されることと ack/nak を検証する
 * なぜ: JetStream 購読設定とハンドラ連携を最低限保証するため
 */
package com.example.notification.nats;

import com.example.notification.config.NotificationNatsProperties;
import com.example.notification.service.NotificationEventHandler;
import com.example.notification.service.NotificationEventPermanentException;
import com.example.proto.entitlement.EntitlementEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.Error;
import io.nats.client.api.StreamConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;

@ExtendWith(MockitoExtension.class)
class EntitlementEventSubscribeTest {

        private static final String SUBJECT = "entitlement.events";
        private static final String STREAM = "entitlement-events";
        private static final String DURABLE = "notification-entitlement-consumer";
        private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(2);
        private static final Duration ACK_WAIT = Duration.ofSeconds(10);
        private static final int MAX_DELIVER = 10;
        private static final long EVENT_VERSION = 1L; // テスト専用の固定バージョン
        private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";

        @Mock
        private Connection connection;

        @Mock
        private JetStream jetStream;

        @Mock
        private JetStreamManagement jetStreamManagement;

        @Mock
        private Dispatcher dispatcher;

        @Mock
        private JetStreamSubscription subscription;

        @Mock
        private NotificationEventHandler eventHandler;

        @Captor
        private ArgumentCaptor<MessageHandler> handlerCaptor;

        @Captor
        private ArgumentCaptor<PushSubscribeOptions> optionsCaptor;

        private EntitlementEventSubscriber subscriber;

        @BeforeEach
        void setUp() {
                NotificationNatsProperties properties =
                                new NotificationNatsProperties(SUBJECT, STREAM, DURABLE, DUPLICATE_WINDOW,
                                                ACK_WAIT, MAX_DELIVER);
                subscriber = new EntitlementEventSubscriber(connection, eventHandler, properties);
        }

        @Test
        void ackWhenHandledSuccessfully() throws Exception {
                Message message = mock(Message.class);
                stubConnection();
                when(message.getData()).thenReturn(buildEventBytes());
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), handlerCaptor.capture(), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);

                subscriber.start();

                // JetStream から受け取ったメッセージを handler 経由で処理する
                handlerCaptor.getValue().onMessage(message);

                verify(eventHandler).handleEntitlementEvent(any(EntitlementEvent.class));
                verify(message).ack();
                verify(message, never()).nak();
        }

        @Test
        void nakWhenHandlerFails() throws Exception {
                Message message = mock(Message.class);
                stubConnection();
                when(message.getData()).thenReturn(buildEventBytes());
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), handlerCaptor.capture(), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);
                doThrow(new DataAccessResourceFailureException("boom"))
                                .when(eventHandler)
                                .handleEntitlementEvent(any(EntitlementEvent.class));

                subscriber.start();

                // 失敗時は ack せず nak する
                handlerCaptor.getValue().onMessage(message);

                verify(message, never()).ack();
                verify(message).nak();
        }

        @Test
        void termWhenHandlerFailsPermanently() throws Exception {
                Message message = mock(Message.class);
                stubConnection();
                when(message.getData()).thenReturn(buildEventBytes());
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), handlerCaptor.capture(), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);
                doThrow(new NotificationEventPermanentException("bad payload", new IllegalArgumentException("boom")))
                                .when(eventHandler)
                                .handleEntitlementEvent(any(EntitlementEvent.class));

                subscriber.start();

                handlerCaptor.getValue().onMessage(message);

                verify(message).term();
                verify(message, never()).ack();
                verify(message, never()).nak();
        }

        @Test
        void swallowExceptionWhenNakFailsViaJetStreamHandlerWiring() throws Exception {
                Message message = mock(Message.class);
                stubConnection();
                when(message.getData()).thenReturn(buildEventBytes());
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), handlerCaptor.capture(), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);
                doThrow(new DataAccessResourceFailureException("boom"))
                                .when(eventHandler)
                                .handleEntitlementEvent(any(EntitlementEvent.class));
                doThrow(new IllegalStateException("nak-failed"))
                                .when(message)
                                .nak();

                subscriber.start();

                assertDoesNotThrow(() -> handlerCaptor.getValue().onMessage(message));

                verify(message, never()).ack();
                verify(message).nak();
        }

        @Test
        void termWhenPayloadParseFails() throws Exception {
                Message message = mock(Message.class);
                stubConnection();
                when(message.getData()).thenReturn(new byte[] {(byte) 0x80});
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), handlerCaptor.capture(), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);

                subscriber.start();

                handlerCaptor.getValue().onMessage(message);

                verify(message).term();
                verify(message, never()).ack();
                verify(message, never()).nak();
        }

        @Test
        void startEnsuresStreamWithDuplicateWindow() throws Exception {
                stubConnection();
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), any(MessageHandler.class), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);
                when(jetStreamManagement.updateStream(any(StreamConfiguration.class)))
                                .thenThrow(streamNotFound());

                subscriber.start();

                verify(jetStreamManagement).updateStream(any(StreamConfiguration.class));
                verify(jetStreamManagement).addStream(any(StreamConfiguration.class));
        }

        @Test
        void startUsesAckWaitAndMaxDeliver() throws Exception {
                stubConnection();
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), handlerCaptor.capture(), eq(false),
                                optionsCaptor.capture()))
                                .thenReturn(subscription);

                subscriber.start();

                PushSubscribeOptions options = optionsCaptor.getValue();
                assertEquals(ACK_WAIT, options.getConsumerConfiguration().getAckWait());
                assertEquals(MAX_DELIVER, options.getConsumerConfiguration().getMaxDeliver());
        }

        @Test
        void stopClosesSubscriptionAndDispatcherSafelyWhenCalledMultipleTimes() throws Exception {
                stubConnection();
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), any(MessageHandler.class), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);

                subscriber.start();

                assertDoesNotThrow(subscriber::stop);
                assertDoesNotThrow(subscriber::stop);

                verify(subscription, times(1)).unsubscribe();
                verify(connection, times(1)).closeDispatcher(dispatcher);
        }

        private void stubConnection() throws IOException {
                when(connection.jetStream()).thenReturn(jetStream);
                when(connection.jetStreamManagement()).thenReturn(jetStreamManagement);
                when(connection.createDispatcher()).thenReturn(dispatcher);
        }

        private byte[] buildEventBytes() {
                EntitlementEvent event = EntitlementEvent.newBuilder()
                                .setEventId(EVENT_ID)
                                .setEventType(EntitlementEvent.EventType.ENTITLEMENT_GRANTED)
                                .setOccurredAt("2026-01-12T00:00:00Z")
                                .setUserId("user-1")
                                .setStockKeepingUnit("sku-1")
                                .setSource("entitlement")
                                .setSourceId("source-1")
                                .setVersion(EVENT_VERSION)
                                .setTraceId("trace-1")
                                .build();
                return event.toByteArray();
        }

        private JetStreamApiException streamNotFound() {
                return new StreamNotFoundException();
        }

        private static final class StreamNotFoundException extends JetStreamApiException {
                private StreamNotFoundException() {
                        super(Error.JsBadRequestErr);
                }

                @Override
                public int getApiErrorCode() {
                        return 10059;
                }

                @Override
                public int getErrorCode() {
                        return 404;
                }
        }
}
