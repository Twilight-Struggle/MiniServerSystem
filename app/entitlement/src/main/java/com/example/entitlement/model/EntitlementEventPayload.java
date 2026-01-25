/*
 * どこで: Entitlement ドメインモデル
 * 何を: Outbox payload として保存するイベント内容を定義する
 * なぜ: NATS publish 時に必要な情報を再構築するため
 */
package com.example.entitlement.model;

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
