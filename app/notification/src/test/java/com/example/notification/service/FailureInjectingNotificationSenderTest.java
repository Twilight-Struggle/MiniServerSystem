/*
 * どこで: Notification 送信層のユニットテスト
 * 何を: CI/Test 専用失敗注入 Sender の分岐を検証する
 * なぜ: DLQ 検証シナリオの前提が壊れないようにするため
 */
package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FailureInjectingNotificationSenderTest {

  @Test
  void sendThrowsWhenPrefixMatches() {
    final LocalNotificationSender delegate = mock(LocalNotificationSender.class);
    final FailureInjectingNotificationSender sender =
        new FailureInjectingNotificationSender(delegate);
    setUserIdPrefix(sender, "e2e-dlq-");

    assertThatThrownBy(() -> sender.send(notificationRecord("e2e-dlq-user-1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("failure injection");
  }

  @Test
  void sendDelegatesWhenPrefixDoesNotMatch() {
    final LocalNotificationSender delegate = mock(LocalNotificationSender.class);
    final FailureInjectingNotificationSender sender =
        new FailureInjectingNotificationSender(delegate);
    setUserIdPrefix(sender, "e2e-dlq-");
    final NotificationRecord record = notificationRecord("normal-user-1");

    assertThatCode(() -> sender.send(record)).doesNotThrowAnyException();

    verify(delegate).send(record);
  }

  @Test
  void sendDelegatesWhenPrefixIsBlank() {
    final LocalNotificationSender delegate = mock(LocalNotificationSender.class);
    final FailureInjectingNotificationSender sender =
        new FailureInjectingNotificationSender(delegate);
    setUserIdPrefix(sender, "");
    final NotificationRecord record = notificationRecord("e2e-dlq-user-1");

    assertThatCode(() -> sender.send(record)).doesNotThrowAnyException();

    verify(delegate).send(record);
  }

  private void setUserIdPrefix(FailureInjectingNotificationSender sender, String prefix) {
    try {
      final Field field = FailureInjectingNotificationSender.class.getDeclaredField("userIdPrefix");
      field.setAccessible(true);
      field.set(sender, prefix);
    } catch (ReflectiveOperationException ex) {
      throw new AssertionError("failed to set userIdPrefix for test setup", ex);
    }
  }

  private NotificationRecord notificationRecord(String userId) {
    final Instant now = Instant.parse("2026-01-17T00:00:00Z");
    return new NotificationRecord(
        UUID.randomUUID(),
        UUID.randomUUID(),
        userId,
        "EntitlementGranted",
        now,
        "{\"event_id\":\"dummy\"}",
        NotificationStatus.PENDING,
        null,
        null,
        null,
        0,
        now,
        now,
        null);
  }
}
