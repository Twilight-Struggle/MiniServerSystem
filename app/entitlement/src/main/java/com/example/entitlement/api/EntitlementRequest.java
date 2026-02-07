/*
 * どこで: Entitlement API
 * 何を: 付与/剥奪リクエストの入力を保持する
 * なぜ: JSON からのバインドと検証を明確にするため
 */
package com.example.entitlement.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EntitlementRequest(
    @NotBlank(message = "user_id is required") String userId,
    @NotBlank(message = "stock_keeping_unit is required") String stockKeepingUnit,
    @NotBlank(message = "reason is required") String reason,
    @NotBlank(message = "purchase_id is required") String purchaseId) {}
