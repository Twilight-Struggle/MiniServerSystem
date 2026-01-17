/*
 * どこで: Notification API モデル
 * 何を: デバッグ用通知一覧のレスポンス
 * なぜ: API レスポンスの構造を固定するため
 */
package com.example.notification.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NotificationInboxResponse(
        String userId,
        List<NotificationSummary> notifications
) {
}
