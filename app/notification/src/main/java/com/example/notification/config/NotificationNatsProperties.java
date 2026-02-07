/*
 * どこで: Notification アプリの設定バインド
 * 何を: NATS JetStream 購読設定(subject/stream/durable/duplicate-window/ack-wait/max-deliver)を保持する
 * なぜ: 受信トピックと再配信制御の窓を環境で調整し、起動時に妥当性を検証するため
 */
package com.example.notification.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "notification.nats")
@Validated
public record NotificationNatsProperties(
    @NotBlank String subject,
    @NotBlank String stream,
    @NotBlank String durable,
    @NotNull Duration duplicateWindow,
    @NotNull Duration ackWait,
    @NotNull @Positive Integer maxDeliver) {

  @AssertTrue(message = "notification.nats.duplicate-window must be positive")
  public boolean isDuplicateWindowPositive() {
    // Duration には @Positive が使えないため、ゼロ/負値を明示的に弾く。
    return isPositiveDuration(duplicateWindow);
  }

  @AssertTrue(message = "notification.nats.ack-wait must be positive")
  public boolean isAckWaitPositive() {
    // ack-wait は再配信猶予なので 0 以下は許容しない。
    return isPositiveDuration(ackWait);
  }

  private boolean isPositiveDuration(Duration duration) {
    // null は @NotNull で検出する前提。
    return duration != null && !duration.isZero() && !duration.isNegative();
  }
}
