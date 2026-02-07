/*
 * どこで: Notification アプリの設定バインド
 * 何を: JetStream terminated advisory の subject/stream/durable を保持する
 * なぜ: TERM 後の DLQ 保存対象を明示的に設定するため
 */
package com.example.notification.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "notification.nats.terminated-advisory")
@Validated
public record NotificationNatsTerminatedAdvisoryProperties(
    @NotBlank String subject, @NotBlank String stream, @NotBlank String durable) {}
