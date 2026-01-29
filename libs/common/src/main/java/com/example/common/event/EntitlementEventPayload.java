/*
 * どこで: common のイベント payload 定義
 * 何を: Entitlement の outbox payload を共通レコードとして提供する
 * なぜ: サービス間で同一のペイロード形状を共有するため
 */
package com.example.common.event;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EntitlementEventPayload(
        String eventId,
        String eventType,
        String occurredAt,
        String userId,
        String stockKeepingUnit,
        String source,
        String sourceId,
        long version,
        String traceId) {
}
