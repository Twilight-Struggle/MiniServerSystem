/*
 * どこで: Entitlement API
 * 何を: ユーザの権利一覧の要素を表す
 * なぜ: 検索レスポンスを簡潔に保つため
 */
package com.example.entitlement.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EntitlementSummary(
        String stockKeepingUnit,
        String status,
        long version,
        Instant updatedAt) {
}
