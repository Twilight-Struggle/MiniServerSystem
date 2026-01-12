/*
 * どこで: Notification アプリのインフラ設定
 * 何を: NATS Connection を Spring 管理下に置く
 * なぜ: Subscriber が同一接続を再利用するため
 */
package com.example.notification.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import java.io.IOException;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true", matchIfMissing = true)
public class NatsConfig {

    @Bean(destroyMethod = "close")
    public Connection natsConnection(NatsProperties properties) throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(properties.url())
                .connectionTimeout(Duration.ofSeconds(properties.connectionTimeout()))
                .build();
        return Nats.connect(options);
    }
}
