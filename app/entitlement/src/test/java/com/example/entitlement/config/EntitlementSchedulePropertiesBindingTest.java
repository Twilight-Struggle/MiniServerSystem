/*
 * どこで: Entitlement 設定バインドのスケジュール設定テスト
 * 何を: poll-interval/cleanup-interval の Duration バインドを検証する
 * なぜ: ms 表記から Duration へ変更した設定が起動時に正しく解釈されることを保証するため
 */
package com.example.entitlement.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class EntitlementSchedulePropertiesBindingTest {

    // Duration 文字列のバインドだけを検証する最小構成
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "entitlement.outbox.enabled=true",
                    "entitlement.outbox.poll-interval=1s",
                    "entitlement.outbox.batch-size=50",
                    "entitlement.outbox.max-attempts=10",
                    "entitlement.outbox.backoff-base=1s",
                    "entitlement.outbox.backoff-max=60s",
                    "entitlement.outbox.backoff-exponent-base=2.0",
                    "entitlement.outbox.backoff-jitter-min=0.5",
                    "entitlement.outbox.backoff-jitter-max=1.5",
                    "entitlement.outbox.backoff-min=1s",
                    "entitlement.outbox.error-message-max-length=1000",
                    "entitlement.outbox.lease=30s",
                    "entitlement.outbox.published-ttl=24h",
                    "entitlement.retention.enabled=true",
                    "entitlement.retention.cleanup-interval=1h"
            );

    @Test
    void contextStartsAndBindsDurationFields() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            EntitlementOutboxProperties outboxProperties = context.getBean(EntitlementOutboxProperties.class);
            EntitlementRetentionProperties retentionProperties = context.getBean(EntitlementRetentionProperties.class);

            assertThat(outboxProperties.pollInterval()).isEqualTo(Duration.ofSeconds(1));
            assertThat(outboxProperties.backoffBase()).isEqualTo(Duration.ofSeconds(1));
            assertThat(outboxProperties.backoffMax()).isEqualTo(Duration.ofSeconds(60));
            assertThat(outboxProperties.backoffMin()).isEqualTo(Duration.ofSeconds(1));
            assertThat(outboxProperties.lease()).isEqualTo(Duration.ofSeconds(30));
            assertThat(outboxProperties.publishedTtl()).isEqualTo(Duration.ofHours(24));
            assertThat(retentionProperties.cleanupInterval()).isEqualTo(Duration.ofHours(1));
            // 既存の値も合わせてバインドされることを簡単に確認する
            assertThat(outboxProperties.batchSize()).isEqualTo(50);
            assertThat(outboxProperties.maxAttempts()).isEqualTo(10);
        });
    }

    @Configuration
    @EnableConfigurationProperties({
            EntitlementOutboxProperties.class,
            EntitlementRetentionProperties.class
    })
    static class TestConfiguration {
        // ApplicationContextRunner 用の最小構成
    }
}
