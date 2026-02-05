/*
 * どこで: Notification NATS 購読
 * 何を: MaxDeliver advisory を購読して stream_seq を DLQ 保存する
 * なぜ: Subscriber 未処理の再投入対象を特定できるようにするため
 */
package com.example.notification.nats;

import com.example.notification.config.NotificationNatsAdvisoryProperties;
import com.example.notification.config.NotificationNatsProperties;
import com.example.notification.repository.NotificationNatsDlqRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
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
import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationMaxDeliverAdvisorySubscriber {

    private static final Logger logger = LoggerFactory.getLogger(NotificationMaxDeliverAdvisorySubscriber.class);
    private static final int STREAM_NOT_FOUND_ERROR = 404;
    private static final int STREAM_NOT_FOUND_API_ERROR = 10059;

    private final Connection connection;
    private final NotificationNatsProperties natsProperties;
    private final NotificationNatsAdvisoryProperties advisoryProperties;
    private final NotificationNatsDlqRepository dlqRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AtomicBoolean started;
    private Dispatcher dispatcher;
    private JetStreamSubscription subscription;

    public NotificationMaxDeliverAdvisorySubscriber(Connection connection,
            NotificationNatsProperties natsProperties,
            NotificationNatsAdvisoryProperties advisoryProperties,
            NotificationNatsDlqRepository dlqRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.connection = connection;
        this.natsProperties = natsProperties;
        this.advisoryProperties = advisoryProperties;
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.started = new AtomicBoolean(false);
    }

    @PostConstruct
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            ensureStream();
            JetStream jetStream = connection.jetStream();
            dispatcher = connection.createDispatcher();
            subscription = jetStream.subscribe(
                    advisoryProperties.subject(),
                    dispatcher,
                    this::handleMessage,
                    false,
                    buildPushSubscribeOptions());
            logger.info("notification advisory subscriber started subject={} stream={} durable={}",
                    advisoryProperties.subject(),
                    advisoryProperties.stream(),
                    advisoryProperties.durable());
        } catch (IOException | JetStreamApiException ex) {
            started.set(false);
            throw new IllegalStateException("failed to start JetStream advisory subscription", ex);
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
            OptionalLong streamSeq = extractStreamSeq(message);
            if (streamSeq.isEmpty()) {
                // stream_seq が取れない advisory は再処理に使えないため破棄する
                logger.warn("advisory payload missing stream_seq subject={}", advisoryProperties.subject());
                ackSilently(message);
                return;
            }
            dlqRepository.insert(streamSeq.getAsLong(), Instant.now(clock));
            message.ack();
        } catch (IOException ex) {
            // 不正 JSON は再配信しても回復しないため ack で破棄する
            logger.warn("failed to parse advisory payload subject={}", advisoryProperties.subject(), ex);
            ackSilently(message);
        } catch (DataAccessException ex) {
            // DB 障害は復旧後に再処理できるよう nak で再配信させる
            logger.warn("temporary failure while handling advisory payload subject={}",
                    advisoryProperties.subject(), ex);
            nakSilently(message);
        } catch (RuntimeException ex) {
            // 想定外の失敗は再配信で保全する
            logger.warn("failed to handle advisory payload subject={}", advisoryProperties.subject(), ex);
            nakSilently(message);
        }
    }

    private OptionalLong extractStreamSeq(Message message) throws IOException {
        JsonNode payload = objectMapper.readTree(message.getData());
        JsonNode streamSeqNode = payload.get("stream_seq");
        if (streamSeqNode == null || !streamSeqNode.canConvertToLong()) {
            return OptionalLong.empty();
        }
        long streamSeq = streamSeqNode.asLong();
        if (streamSeq <= 0L) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(streamSeq);
    }

    private void ensureStream() throws IOException, JetStreamApiException {
        StreamConfiguration streamConfiguration = StreamConfiguration.builder()
                .name(advisoryProperties.stream())
                .subjects(advisoryProperties.subject())
                .build();
        JetStreamManagement jetStreamManagement = connection.jetStreamManagement();
        upsertStream(jetStreamManagement, streamConfiguration);
        logger.info("notification advisory stream ensured stream={} subject={}",
                advisoryProperties.stream(),
                advisoryProperties.subject());
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
        ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder()
                .ackPolicy(AckPolicy.Explicit)
                // MaxDeliver advisory も同じ再配信制御値を流用する
                .ackWait(natsProperties.ackWait())
                .maxDeliver(natsProperties.maxDeliver())
                .build();
        return PushSubscribeOptions.builder()
                .stream(advisoryProperties.stream())
                .durable(advisoryProperties.durable())
                .configuration(consumerConfiguration)
                .build();
    }

    private void nakSilently(Message message) {
        try {
            message.nak();
        } catch (IllegalStateException ex) {
            logger.warn("failed to nack advisory message", ex);
        }
    }

    private void ackSilently(Message message) {
        try {
            message.ack();
        } catch (IllegalStateException ex) {
            logger.warn("failed to ack advisory message", ex);
        }
    }
}
