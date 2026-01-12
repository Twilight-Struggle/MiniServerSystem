/*
 * どこで: Notification アプリの設定バインド
 * 何を: JetStream MaxDeliver advisory の subject/stream/durable を保持する
 * なぜ: MaxDeliver 超過時の DLQ 保存対象を明示的に設定するため
 */
package com.example.notification.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "notification.nats.advisory")
@Validated
public record NotificationNatsAdvisoryProperties(
        @NotBlank String subject,
        @NotBlank String stream,
        @NotBlank String durable) {
}
