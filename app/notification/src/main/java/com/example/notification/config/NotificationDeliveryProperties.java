/*
 * どこで: Notification アプリの設定バインド
 * 何を: 通知処理のポーリング/リトライ設定を保持する
 * なぜ: 運用パラメータを外部化するため
 */
package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.delivery")
public record NotificationDeliveryProperties(
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
        long leaseSeconds
) {
}
