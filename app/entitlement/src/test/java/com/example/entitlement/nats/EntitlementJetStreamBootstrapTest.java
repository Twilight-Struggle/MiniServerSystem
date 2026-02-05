/*
 * どこで: Entitlement JetStream 初期化テスト
 * 何を: stream 作成時に duplicate window が設定されることを確認する
 * なぜ: Nats-Msg-Id の重複排除を有効化する設定を保証するため
 */
package com.example.entitlement.nats;

import com.example.entitlement.config.EntitlementNatsProperties;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.Error;
import io.nats.client.api.StreamConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementJetStreamBootstrapTest {

    private static final EntitlementNatsProperties PROPERTIES = new EntitlementNatsProperties(
            "entitlement.events",
            "entitlement-events",
            Duration.ofMinutes(2));

    @Mock
    private Connection connection;

    @Mock
    private JetStreamManagement jetStreamManagement;

    @Test
    void startCreatesStreamWithDuplicateWindow() throws Exception {
        when(connection.jetStreamManagement()).thenReturn(jetStreamManagement);
        when(jetStreamManagement.updateStream(any(StreamConfiguration.class)))
                .thenThrow(streamNotFound());

        EntitlementJetStreamBootstrap bootstrap = new EntitlementJetStreamBootstrap(connection, PROPERTIES);
        bootstrap.start();

        verify(jetStreamManagement).updateStream(any(StreamConfiguration.class));
        verify(jetStreamManagement).addStream(any(StreamConfiguration.class));
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
