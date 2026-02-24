package com.example.notification.nats;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.config.NotificationMatchmakingNatsProperties;
import com.example.notification.service.MatchmakingEventHandler;
import com.example.notification.service.NotificationEventPermanentException;
import com.example.proto.matchmaking.MatchmakingEvent;
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
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class MatchmakingEventSubscribeTest {

  private static final String SUBJECT = "matchmaking.events";
  private static final String STREAM = "matchmaking-events";
  private static final String DURABLE = "notification-matchmaking-consumer";
  private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(2);
  private static final Duration ACK_WAIT = Duration.ofSeconds(10);
  private static final int MAX_DELIVER = 10;

  @Mock private Connection connection;
  @Mock private JetStream jetStream;
  @Mock private JetStreamManagement jetStreamManagement;
  @Mock private Dispatcher dispatcher;
  @Mock private JetStreamSubscription subscription;
  @Mock private MatchmakingEventHandler eventHandler;
  @Captor private ArgumentCaptor<MessageHandler> handlerCaptor;
  @Captor private ArgumentCaptor<PushSubscribeOptions> optionsCaptor;

  private MatchmakingEventSubscriber subscriber;

  @BeforeEach
  void setUp() {
    final NotificationMatchmakingNatsProperties properties =
        new NotificationMatchmakingNatsProperties(
            SUBJECT, STREAM, DURABLE, DUPLICATE_WINDOW, ACK_WAIT, MAX_DELIVER);
    subscriber = new MatchmakingEventSubscriber(connection, eventHandler, properties);
  }

  @Test
  void ackWhenHandledSuccessfully()
      throws IOException, InterruptedException, JetStreamApiException {
    final Message message = org.mockito.Mockito.mock(Message.class);
    stubConnection();
    when(message.getData()).thenReturn(buildEventBytes());
    doReturn(subscription)
        .when(jetStream)
        .subscribe(
            eq(SUBJECT),
            eq(dispatcher),
            handlerCaptor.capture(),
            eq(false),
            any(PushSubscribeOptions.class));

    subscriber.start();
    handlerCaptor.getValue().onMessage(message);

    verify(eventHandler).handleMatchmakingEvent(any(MatchmakingEvent.class));
    verify(message).ack();
    verify(message, never()).nak();
    verify(message, never()).term();
  }

  @Test
  void nakWhenHandlerFails() throws IOException, InterruptedException, JetStreamApiException {
    final Message message = org.mockito.Mockito.mock(Message.class);
    stubConnection();
    when(message.getData()).thenReturn(buildEventBytes());
    doReturn(subscription)
        .when(jetStream)
        .subscribe(
            eq(SUBJECT),
            eq(dispatcher),
            handlerCaptor.capture(),
            eq(false),
            any(PushSubscribeOptions.class));
    doThrow(new DataAccessResourceFailureException("boom"))
        .when(eventHandler)
        .handleMatchmakingEvent(any(MatchmakingEvent.class));

    subscriber.start();
    handlerCaptor.getValue().onMessage(message);

    verify(message).nak();
    verify(message, never()).ack();
  }

  @Test
  void termWhenHandlerFailsPermanently()
      throws IOException, InterruptedException, JetStreamApiException {
    final Message message = org.mockito.Mockito.mock(Message.class);
    stubConnection();
    when(message.getData()).thenReturn(buildEventBytes());
    doReturn(subscription)
        .when(jetStream)
        .subscribe(
            eq(SUBJECT),
            eq(dispatcher),
            handlerCaptor.capture(),
            eq(false),
            any(PushSubscribeOptions.class));
    doThrow(new NotificationEventPermanentException("bad", new IllegalArgumentException("x")))
        .when(eventHandler)
        .handleMatchmakingEvent(any(MatchmakingEvent.class));

    subscriber.start();
    handlerCaptor.getValue().onMessage(message);

    verify(message).term();
    verify(message, never()).ack();
    verify(message, never()).nak();
  }

  @Test
  void termWhenPayloadParseFails() throws IOException, InterruptedException, JetStreamApiException {
    final Message message = org.mockito.Mockito.mock(Message.class);
    stubConnection();
    when(message.getData()).thenReturn(new byte[] {(byte) 0x80});
    doReturn(subscription)
        .when(jetStream)
        .subscribe(
            eq(SUBJECT),
            eq(dispatcher),
            handlerCaptor.capture(),
            eq(false),
            any(PushSubscribeOptions.class));

    subscriber.start();
    handlerCaptor.getValue().onMessage(message);

    verify(message).term();
  }

  @Test
  void startEnsuresStreamWithDuplicateWindow() throws IOException, JetStreamApiException {
    stubConnection();
    doReturn(subscription)
        .when(jetStream)
        .subscribe(
            eq(SUBJECT),
            eq(dispatcher),
            any(MessageHandler.class),
            eq(false),
            any(PushSubscribeOptions.class));
    doThrow(streamNotFound())
        .when(jetStreamManagement)
        .updateStream(any(StreamConfiguration.class));

    subscriber.start();

    verify(jetStreamManagement).updateStream(any(StreamConfiguration.class));
    verify(jetStreamManagement).addStream(any(StreamConfiguration.class));
  }

  @Test
  void startUsesAckWaitAndMaxDeliver() throws IOException, JetStreamApiException {
    stubConnection();
    doReturn(subscription)
        .when(jetStream)
        .subscribe(
            eq(SUBJECT),
            eq(dispatcher),
            handlerCaptor.capture(),
            eq(false),
            optionsCaptor.capture());

    subscriber.start();

    final PushSubscribeOptions options = optionsCaptor.getValue();
    assertEquals(ACK_WAIT, options.getConsumerConfiguration().getAckWait());
    assertEquals(MAX_DELIVER, options.getConsumerConfiguration().getMaxDeliver());
  }

  @Test
  void stopClosesSubscriptionAndDispatcherSafelyWhenCalledMultipleTimes()
      throws IOException, JetStreamApiException {
    stubConnection();
    doReturn(subscription)
        .when(jetStream)
        .subscribe(
            eq(SUBJECT),
            eq(dispatcher),
            any(MessageHandler.class),
            eq(false),
            any(PushSubscribeOptions.class));

    subscriber.start();

    assertDoesNotThrow(subscriber::stop);
    assertDoesNotThrow(subscriber::stop);

    verify(subscription, times(1)).unsubscribe();
    verify(connection, times(1)).closeDispatcher(dispatcher);
  }

  private void stubConnection() throws IOException {
    doReturn(jetStream).when(connection).jetStream();
    doReturn(jetStreamManagement).when(connection).jetStreamManagement();
    when(connection.createDispatcher()).thenReturn(dispatcher);
  }

  private byte[] buildEventBytes() {
    return MatchmakingEvent.newBuilder()
        .setEventId("22222222-2222-2222-2222-222222222222")
        .setEventType(MatchmakingEvent.EventType.MATCH_FOUND)
        .setOccurredAt("2026-02-24T11:59:30Z")
        .setMatchId("match-1")
        .setMode("casual")
        .setTraceId("trace-1")
        .build()
        .toByteArray();
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
