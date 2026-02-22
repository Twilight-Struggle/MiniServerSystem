/*
 * どこで: Notification サービス層
 * 何を: CI/Test 専用で通知送信失敗を注入する Sender
 * なぜ: 実コード経路を汚さずに E2E で retry -> DLQ を再現するため
 */
package com.example.notification.service;

import com.example.notification.model.NotificationRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
@Profile({"ci", "test"})
@ConditionalOnProperty(
    prefix = "notification.delivery.failure-injection",
    name = "enabled",
    havingValue = "true")
public class FailureInjectingNotificationSender implements NotificationSender {

  private final LocalNotificationSender delegate;

  @Value("${notification.delivery.failure-injection.user-id-prefix:}")
  private String userIdPrefix;

  @Override
  public void send(NotificationRecord record) {
    if (shouldInjectFailure(record.userId())) {
      throw new IllegalStateException(
          "notification delivery failure injection matched userId=" + record.userId());
    }
    delegate.send(record);
  }

  private boolean shouldInjectFailure(String userId) {
    if (userIdPrefix == null || userIdPrefix.isBlank()) {
      return false;
    }
    return userId.startsWith(userIdPrefix);
  }
}
