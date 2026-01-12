/*
 * どこで: Notification アプリの設定バインド
 * 何を: NATS JetStream 購読設定(subject/stream/durable)を保持する
 * なぜ: 受信トピックと consumer を環境で調整するため
 */
package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.nats")
public record NotificationNatsProperties(String subject, String stream, String durable) {
}
