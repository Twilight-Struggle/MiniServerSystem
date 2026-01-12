/*
 * どこで: Entitlement NATS 初期化
 * 何を: JetStream stream を起動時に作成/更新する
 * なぜ: publish 前に stream を確保し Nats-Msg-Id の重複排除を有効化するため
 */
package com.example.entitlement.nats;

import com.example.entitlement.config.EntitlementNatsProperties;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamConfiguration;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "entitlement.outbox.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EntitlementJetStreamBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(EntitlementJetStreamBootstrap.class);
    private static final int STREAM_NOT_FOUND_ERROR = 404;
    private static final int STREAM_NOT_FOUND_API_ERROR = 10059;

    private final Connection connection;
    private final EntitlementNatsProperties properties;

    @PostConstruct
    public void start() {
        ensureSettings();
        try {
            // Nats-Msg-Id の重複排除は stream の duplicate window が前提になる
            StreamConfiguration streamConfiguration = StreamConfiguration.builder()
                    .name(properties.stream())
                    .subjects(properties.subject())
                    .duplicateWindow(properties.duplicateWindow())
                    .build();
            JetStreamManagement jetStreamManagement = connection.jetStreamManagement();
            upsertStream(jetStreamManagement, streamConfiguration);
            logger.info("entitlement stream ensured stream={} subject={} duplicateWindow={}",
                    properties.stream(),
                    properties.subject(),
                    properties.duplicateWindow());
        } catch (IOException | JetStreamApiException ex) {
            throw new IllegalStateException("failed to ensure JetStream stream", ex);
        }
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

    private void ensureSettings() {
        if (properties.stream() == null || properties.stream().isBlank()) {
            throw new IllegalStateException("entitlement.nats.stream must be set");
        }
        if (properties.duplicateWindow() == null) {
            throw new IllegalStateException("entitlement.nats.duplicate-window must be set");
        }
        if (properties.duplicateWindow().isZero() || properties.duplicateWindow().isNegative()) {
            throw new IllegalStateException("entitlement.nats.duplicate-window must be positive");
        }
    }
}
