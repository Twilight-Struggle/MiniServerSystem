/*
 * どこで: Entitlement API
 * 何を: 付与/剥奪結果のレスポンスを表す
 * なぜ: API 仕様に沿った JSON を返すため
 */
package com.example.entitlement.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EntitlementResponse(
    String userId, String stockKeepingUnit, String status, long version, Instant updatedAt) {}
