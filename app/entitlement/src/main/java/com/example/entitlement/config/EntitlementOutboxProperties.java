/*
 * どこで: Entitlement アプリの設定バインド
 * 何を: Outbox publish のポーリング/リトライ設定を保持する
 * なぜ: 運用パラメータを外部化するため
 */
package com.example.entitlement.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "entitlement.outbox")
public record EntitlementOutboxProperties(
                boolean enabled,
                Duration pollInterval,
                int batchSize,
                int maxAttempts,
                Duration backoffBase,
                Duration backoffMax,
                double backoffExponentBase,
                double backoffJitterMin,
                double backoffJitterMax,
                Duration backoffMin,
                int errorMessageMaxLength,
                Duration lease,
                Duration publishedTtl) {
}
