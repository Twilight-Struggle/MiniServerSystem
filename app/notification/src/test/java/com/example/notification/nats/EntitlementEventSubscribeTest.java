/*
 * どこで: Notification NATS JetStream 購読テスト(統合寄り)
 * 何を: start() 経由で handleMessage が配線されることと ack/nak を検証する
 * なぜ: JetStream 購読設定とハンドラ連携を最低限保証するため
 */
package com.example.notification.nats;

import com.example.notification.config.NotificationNatsProperties;
import com.example.notification.service.NotificationEventHandler;
import com.example.proto.entitlement.EntitlementEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class EntitlementEventSubscribeTest {

        private static final String SUBJECT = "entitlement.events";
        private static final String STREAM = "entitlement-events";
        private static final String DURABLE = "notification-entitlement-consumer";
        private static final long EVENT_VERSION = 1L; // テスト専用の固定バージョン
        private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";

        @Mock
        private Connection connection;

        @Mock
        private JetStream jetStream;

        @Mock
        private Dispatcher dispatcher;

        @Mock
        private JetStreamSubscription subscription;

        @Mock
        private NotificationEventHandler eventHandler;

        @Captor
        private ArgumentCaptor<MessageHandler> handlerCaptor;

        private EntitlementEventSubscriber subscriber;

        @BeforeEach
        void setUp() throws IOException {
                NotificationNatsProperties properties = new NotificationNatsProperties(SUBJECT, STREAM, DURABLE);
                when(connection.jetStream()).thenReturn(jetStream);
                when(connection.createDispatcher()).thenReturn(dispatcher);
                subscriber = new EntitlementEventSubscriber(connection, eventHandler, properties);
        }

        @Test
        void ackWhenHandledSuccessfully() throws Exception {
                Message message = mock(Message.class);
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
                when(message.getData()).thenReturn(buildEventBytes());
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), handlerCaptor.capture(), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);
                doThrow(new IllegalArgumentException("boom"))
                                .when(eventHandler)
                                .handleEntitlementEvent(any(EntitlementEvent.class));

                subscriber.start();

                // 失敗時は ack せず nak する
                handlerCaptor.getValue().onMessage(message);

                verify(message, never()).ack();
                verify(message).nak();
        }

        @Test
        void swallowExceptionWhenNakFailsViaJetStreamHandlerWiring() throws Exception {
                Message message = mock(Message.class);
                when(message.getData()).thenReturn(buildEventBytes());
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), handlerCaptor.capture(), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);
                doThrow(new IllegalArgumentException("boom"))
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
        void ensureJetStreamSettingsFailsWhenStreamIsEmpty() {
                NotificationNatsProperties properties = new NotificationNatsProperties(SUBJECT, "", DURABLE);
                EntitlementEventSubscriber invalidSubscriber =
                                new EntitlementEventSubscriber(connection, eventHandler, properties);

                assertThrows(IllegalStateException.class, invalidSubscriber::start);
        }

        @Test
        void ensureJetStreamSettingsFailsWhenDurableIsEmpty() {
                NotificationNatsProperties properties = new NotificationNatsProperties(SUBJECT, STREAM, "");
                EntitlementEventSubscriber invalidSubscriber =
                                new EntitlementEventSubscriber(connection, eventHandler, properties);

                assertThrows(IllegalStateException.class, invalidSubscriber::start);
        }

        @Test
        void stopClosesSubscriptionAndDispatcherSafelyWhenCalledMultipleTimes() throws Exception {
                when(jetStream.subscribe(eq(SUBJECT), eq(dispatcher), any(MessageHandler.class), eq(false),
                                any(PushSubscribeOptions.class)))
                                .thenReturn(subscription);

                subscriber.start();

                assertDoesNotThrow(subscriber::stop);
                assertDoesNotThrow(subscriber::stop);

                verify(subscription, times(1)).unsubscribe();
                verify(connection, times(1)).closeDispatcher(dispatcher);
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
}
