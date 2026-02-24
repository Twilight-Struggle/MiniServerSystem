package com.example.notification.nats;

import com.example.notification.config.NotificationMatchmakingNatsProperties;
import com.example.notification.service.MatchmakingEventHandler;
import com.example.notification.service.NotificationEventPermanentException;
import com.example.proto.matchmaking.MatchmakingEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.StreamConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true", matchIfMissing = true)
public class MatchmakingEventSubscriber {

  private static final Logger logger = LoggerFactory.getLogger(MatchmakingEventSubscriber.class);
  private static final int STREAM_NOT_FOUND_ERROR = 404;
  private static final int STREAM_NOT_FOUND_API_ERROR = 10059;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "NATS Connection は外部管理の共有リソースで、防御的コピーが不可能なため")
  private final Connection connection;

  private final MatchmakingEventHandler eventHandler;
  private final NotificationMatchmakingNatsProperties properties;
  private final AtomicBoolean started;
  private Dispatcher dispatcher;
  private JetStreamSubscription subscription;

  public MatchmakingEventSubscriber(
      Connection connection,
      MatchmakingEventHandler eventHandler,
      NotificationMatchmakingNatsProperties properties) {
    this.connection = connection;
    this.eventHandler = eventHandler;
    this.properties = properties;
    this.started = new AtomicBoolean(false);
  }

  @PostConstruct
  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    try {
      ensureStream();
      final JetStream jetStream = connection.jetStream();
      dispatcher = connection.createDispatcher();
      subscription =
          jetStream.subscribe(
              properties.subject(),
              dispatcher,
              this::handleMessage,
              false,
              buildPushSubscribeOptions());
      logger.info(
          "matchmaking subscriber started subject={} stream={} durable={}",
          properties.subject(),
          properties.stream(),
          properties.durable());
    } catch (IOException | JetStreamApiException ex) {
      started.set(false);
      throw new IllegalStateException("failed to start matchmaking subscriber", ex);
    }
  }

  @PreDestroy
  public void stop() {
    if (subscription != null) {
      subscription.unsubscribe();
      subscription = null;
    }
    if (dispatcher != null) {
      connection.closeDispatcher(dispatcher);
      dispatcher = null;
    }
  }

  void handleMessage(Message message) {
    try {
      final MatchmakingEvent event = MatchmakingEvent.parseFrom(message.getData());
      eventHandler.handleMatchmakingEvent(event);
      message.ack();
    } catch (InvalidProtocolBufferException ex) {
      message.term();
    } catch (NotificationEventPermanentException ex) {
      message.term();
    } catch (DataAccessException ex) {
      message.nak();
    } catch (RuntimeException ex) {
      message.nak();
    }
  }

  private void ensureStream() throws IOException, JetStreamApiException {
    final StreamConfiguration streamConfiguration =
        StreamConfiguration.builder()
            .name(properties.stream())
            .subjects(properties.subject())
            .duplicateWindow(properties.duplicateWindow())
            .build();
    final JetStreamManagement jetStreamManagement = connection.jetStreamManagement();
    try {
      jetStreamManagement.updateStream(streamConfiguration);
    } catch (JetStreamApiException ex) {
      if (!isStreamNotFound(ex)) {
        throw ex;
      }
      jetStreamManagement.addStream(streamConfiguration);
    }
  }

  private boolean isStreamNotFound(JetStreamApiException ex) {
    return ex.getApiErrorCode() == STREAM_NOT_FOUND_API_ERROR
        || ex.getErrorCode() == STREAM_NOT_FOUND_ERROR;
  }

  private PushSubscribeOptions buildPushSubscribeOptions() {
    final ConsumerConfiguration consumerConfiguration =
        ConsumerConfiguration.builder()
            .ackPolicy(AckPolicy.Explicit)
            .ackWait(properties.ackWait())
            .maxDeliver(properties.maxDeliver())
            .build();
    return PushSubscribeOptions.builder().stream(properties.stream())
        .durable(properties.durable())
        .configuration(consumerConfiguration)
        .build();
  }
}
