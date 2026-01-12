/*
 * どこで: Notification モデル
 * 何を: 通知 payload の JSON 表現
 * なぜ: デバッグ API で内容を確認できるようにするため
 */
package com.example.notification.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NotificationFromEntitlementPayload(
        String eventId,
        String eventType,
        String userId,
        String stockKeepingUnit,
        String source,
        String sourceId,
        long version,
        String traceId) {
}
