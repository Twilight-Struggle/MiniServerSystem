/*
 * どこで: Notification NATS 購読
 * 何を: Entitlement イベントを購読しハンドラへ渡す
 * なぜ: 権利変更イベントを通知処理に繋ぐため
 */
package com.example.notification.nats;

import com.example.notification.config.NotificationNatsProperties;
import com.example.notification.service.NotificationEventHandler;
import com.example.notification.service.NotificationEventPermanentException;
import com.example.proto.entitlement.EntitlementEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;

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
    private static final int STREAM_NOT_FOUND_ERROR = 404;
    private static final int STREAM_NOT_FOUND_API_ERROR = 10059;

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
            ensureStream();
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
            // JetStream 明示 ack: 成功時は ack して再配信を止める
            message.ack();
        } catch (InvalidProtocolBufferException ex) {
            // payload 破損は再配信で回復しないため恒久的に破棄する
            logger.warn("failed to parse nats message payload", ex);
            ackSilently(message);
        } catch (NotificationEventPermanentException ex) {
            // ハンドラが恒久的失敗と判断した場合は ack で破棄する
            logger.warn("permanent failure while handling nats message", ex);
            ackSilently(message);
        } catch (DataAccessException ex) {
            // DB など一時的失敗は再配信させる
            logger.warn("temporary failure while handling nats message", ex);
            nakSilently(message);
        } catch (RuntimeException ex) {
            // 不明な例外はデータロス回避のため再配信に倒す
            logger.warn("failed to handle nats message", ex);
            nakSilently(message);
        }
    }

    private void ensureJetStreamSettings() {
        // JetStream durable consumer の設定は起動時に必須チェックする
        if (properties.subject() == null || properties.subject().isBlank()) {
            throw new IllegalStateException("notification.nats.subject must be set");
        }
        if (properties.stream() == null || properties.stream().isBlank()) {
            throw new IllegalStateException("notification.nats.stream must be set");
        }
        if (properties.durable() == null || properties.durable().isBlank()) {
            throw new IllegalStateException("notification.nats.durable must be set");
        }
        if (properties.duplicateWindow() == null) {
            throw new IllegalStateException("notification.nats.duplicate-window must be set");
        }
        if (properties.duplicateWindow().isZero() || properties.duplicateWindow().isNegative()) {
            throw new IllegalStateException("notification.nats.duplicate-window must be positive");
        }
    }

    private void ensureStream() throws IOException, JetStreamApiException {
        // Nats-Msg-Id による重複排除を有効化するため stream を必ず作成する
        StreamConfiguration streamConfiguration = StreamConfiguration.builder()
                .name(properties.stream())
                .subjects(properties.subject())
                .duplicateWindow(properties.duplicateWindow())
                .build();
        JetStreamManagement jetStreamManagement = connection.jetStreamManagement();
        upsertStream(jetStreamManagement, streamConfiguration);
        logger.info("notification stream ensured stream={} subject={} duplicateWindow={}",
                properties.stream(),
                properties.subject(),
                properties.duplicateWindow());
    }

    private void upsertStream(JetStreamManagement jetStreamManagement,
            StreamConfiguration streamConfiguration) throws IOException, JetStreamApiException {
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

    private void ackSilently(Message message) {
        try {
            // 恒久的失敗時は ack で破棄する
            message.ack();
        } catch (IllegalStateException ex) {
            logger.warn("failed to ack nats message", ex);
        }
    }
}
