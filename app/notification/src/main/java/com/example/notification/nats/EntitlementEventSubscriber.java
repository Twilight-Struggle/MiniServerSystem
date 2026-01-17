/*
 * どこで: Notification NATS 購読
 * 何を: Entitlement イベントを購読しハンドラへ渡す
 * なぜ: 権利変更イベントを通知処理に繋ぐため
 */
package com.example.notification.nats;

import com.example.notification.config.NotificationNatsProperties;
import com.example.notification.service.NotificationEventHandler;
import com.example.proto.entitlement.EntitlementEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true", matchIfMissing = true)
public class EntitlementEventSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(EntitlementEventSubscriber.class);

    private final Connection connection;
    private final NotificationEventHandler eventHandler;
    private final NotificationNatsProperties properties;
    private final AtomicBoolean started;
    private Dispatcher dispatcher;
    private JetStreamSubscription subscription;

    public EntitlementEventSubscriber(Connection connection,
            NotificationEventHandler eventHandler,
            NotificationNatsProperties properties) {
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
        ensureJetStreamSettings();
        try {
            JetStream jetStream = connection.jetStream();
            dispatcher = connection.createDispatcher();
            subscription = jetStream.subscribe(
                    properties.subject(),
                    dispatcher,
                    this::handleMessage,
                    false,
                    buildPushSubscribeOptions());
            logger.info("notification subscriber started subject={} stream={} durable={}",
                    properties.subject(),
                    properties.stream(),
                    properties.durable());
        } catch (IOException | JetStreamApiException ex) {
            started.set(false);
            throw new IllegalStateException("failed to start JetStream subscription", ex);
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

    @VisibleForTesting
    void handleMessage(Message message) {
        try {
            EntitlementEvent event = EntitlementEvent.parseFrom(message.getData());
            eventHandler.handleEntitlementEvent(event);
            // JetStream 明示 ack: 成功時のみ ack して再配信を止める
            message.ack();
        } catch (InvalidProtocolBufferException ex) {
            logger.warn("failed to parse nats message payload", ex);
            nakSilently(message);
        } catch (IllegalArgumentException | IllegalStateException | DataAccessException ex) {
            logger.warn("failed to handle nats message", ex);
            nakSilently(message);
        }
    }

    private void ensureJetStreamSettings() {
        // JetStream durable consumer の設定は起動時に必須チェックする
        if (properties.stream() == null || properties.stream().isBlank()) {
            throw new IllegalStateException("notification.nats.stream must be set");
        }
        if (properties.durable() == null || properties.durable().isBlank()) {
            throw new IllegalStateException("notification.nats.durable must be set");
        }
    }

    private PushSubscribeOptions buildPushSubscribeOptions() {
        // durable + explicit ack の consumer 設定を組み立てる
        ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder()
                .ackPolicy(AckPolicy.Explicit)
                .build();
        return PushSubscribeOptions.builder()
                .stream(properties.stream())
                .durable(properties.durable())
                .configuration(consumerConfiguration)
                .build();
    }

    private void nakSilently(Message message) {
        try {
            // 失敗時は nak で再配信させる
            message.nak();
        } catch (IllegalStateException ex) {
            logger.warn("failed to nack nats message", ex);
        }
    }
}
