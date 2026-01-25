/*
 * どこで: Entitlement アプリの設定バインド
 * 何を: Outbox publish のポーリング/リトライ設定を保持する
 * なぜ: 運用パラメータを外部化するため
 */
package com.example.entitlement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "entitlement.outbox")
public record EntitlementOutboxProperties(
        boolean enabled,
        long pollIntervalMs,
        int batchSize,
        int maxAttempts,
        long backoffBaseSeconds,
        long backoffMaxSeconds,
        double backoffExponentBase,
        double backoffJitterMin,
        double backoffJitterMax,
        long backoffMinSeconds,
        int errorMessageMaxLength,
        long leaseSeconds,
        long publishedTtlHours
) {
}
