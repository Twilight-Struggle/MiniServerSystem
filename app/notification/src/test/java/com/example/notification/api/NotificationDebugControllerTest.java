/*
 * どこで: Notification デバッグ API のユニットテスト
 * 何を: ペイロード解析失敗時の例外を検証する
 * なぜ: 想定外のJSONでエラー原因が分かる例外に統一するため
 */
package com.example.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDebugControllerTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-01-17T00:00:00Z");
  private static final String USER_ID = "user-1";
  private static final String EVENT_TYPE = "EntitlementGranted";
  private static final String INVALID_JSON = "{invalid-json";
  private static final int ATTEMPT_COUNT = 0;

  @Mock private NotificationRepository notificationRepository;

  @Test
  void inboxThrowsIllegalArgumentWhenPayloadIsInvalidJson() {
    // 不正JSONのレコードが来た場合に IllegalArgumentException へ揃えることを確認する
    final NotificationRecord record =
        new NotificationRecord(
            UUID.randomUUID(),
            UUID.randomUUID(),
            USER_ID,
            EVENT_TYPE,
            FIXED_NOW,
            INVALID_JSON,
            NotificationStatus.PENDING,
            null,
            null,
            null,
            ATTEMPT_COUNT,
            null,
            FIXED_NOW,
            null);
    when(notificationRepository.findByUserId(USER_ID)).thenReturn(List.of(record));

    final NotificationDebugController controller =
        new NotificationDebugController(notificationRepository, new ObjectMapper());

    assertThatThrownBy(() -> controller.inbox(USER_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .cause()
        .isInstanceOf(JsonProcessingException.class);
    assertThat(record.payloadJson()).contains(INVALID_JSON);
  }
}
