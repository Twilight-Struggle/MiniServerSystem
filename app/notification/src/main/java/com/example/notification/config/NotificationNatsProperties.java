/*
 * どこで: Notification アプリの設定バインド
 * 何を: NATS JetStream 購読設定(subject/stream/durable/duplicate-window)を保持する
 * なぜ: 受信トピックと重複排除の窓を環境で調整するため
 */
package com.example.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.nats")
public record NotificationNatsProperties(String subject, String stream, String durable, Duration duplicateWindow) {
}
