/*
 * どこで: Notification アプリの設定バインド
 * 何を: 通知処理のポーリング/リトライ設定を保持する
 * なぜ: 運用パラメータを外部化するため
 */
package com.example.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.delivery")
public record NotificationDeliveryProperties(
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
    Duration lease) {}
