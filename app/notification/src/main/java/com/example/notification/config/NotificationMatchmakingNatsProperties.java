/*
 * どこで: Notification 設定
 * 何を: matchmaking イベント購読設定を保持する
 * なぜ: entitlement と別 subject/stream/durable を運用で切り替えるため
 */
package com.example.notification.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "notification.matchmaking-nats")
@Validated
public record NotificationMatchmakingNatsProperties(
    @NotBlank String subject,
    @NotBlank String stream,
    @NotBlank String durable,
    @NotNull Duration duplicateWindow,
    @NotNull Duration ackWait,
    @NotNull @Positive Integer maxDeliver) {

  @AssertTrue(message = "notification.matchmaking-nats.duplicate-window must be positive")
  public boolean isDuplicateWindowPositive() {
    return isPositiveDuration(duplicateWindow);
  }

  @AssertTrue(message = "notification.matchmaking-nats.ack-wait must be positive")
  public boolean isAckWaitPositive() {
    return isPositiveDuration(ackWait);
  }

  private boolean isPositiveDuration(Duration duration) {
    return duration != null && !duration.isZero() && !duration.isNegative();
  }
}
