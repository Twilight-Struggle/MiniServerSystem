/*
 * どこで: Notification 設定バインドのスケジュール設定テスト
 * 何を: poll-interval/cleanup-interval の Duration バインドを検証する
 * なぜ: ms/seconds 表記から Duration へ変更した設定が起動時に正しく解釈されることを保証するため
 */
package com.example.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class NotificationSchedulePropertiesBindingTest {

  // Duration 文字列のバインドだけを検証する最小構成
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfiguration.class)
          .withPropertyValues(
              "notification.delivery.enabled=true",
              "notification.delivery.poll-interval=1s",
              "notification.delivery.batch-size=50",
              "notification.delivery.max-attempts=10",
              "notification.delivery.backoff-base=1s",
              "notification.delivery.backoff-max=60s",
              "notification.delivery.backoff-exponent-base=2.0",
              "notification.delivery.backoff-jitter-min=0.5",
              "notification.delivery.backoff-jitter-max=1.5",
              "notification.delivery.backoff-min=1s",
              "notification.delivery.error-message-max-length=1000",
              "notification.delivery.lease=30s",
              "notification.retention.enabled=true",
              "notification.retention.retention-days=30",
              "notification.retention.cleanup-interval=1h");

  @Test
  void contextStartsAndBindsDurationFields() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          final NotificationDeliveryProperties deliveryProperties =
              context.getBean(NotificationDeliveryProperties.class);
          final NotificationRetentionProperties retentionProperties =
              context.getBean(NotificationRetentionProperties.class);

          assertThat(deliveryProperties.pollInterval()).isEqualTo(Duration.ofSeconds(1));
          assertThat(deliveryProperties.backoffBase()).isEqualTo(Duration.ofSeconds(1));
          assertThat(deliveryProperties.backoffMax()).isEqualTo(Duration.ofSeconds(60));
          assertThat(deliveryProperties.backoffMin()).isEqualTo(Duration.ofSeconds(1));
          assertThat(deliveryProperties.lease()).isEqualTo(Duration.ofSeconds(30));
          assertThat(retentionProperties.cleanupInterval()).isEqualTo(Duration.ofHours(1));
          assertThat(retentionProperties.retentionDays()).isEqualTo(30);
        });
  }

  @Configuration
  @EnableConfigurationProperties({
    NotificationDeliveryProperties.class,
    NotificationRetentionProperties.class
  })
  static class TestConfiguration {
    // ApplicationContextRunner 用の最小構成
  }
}
