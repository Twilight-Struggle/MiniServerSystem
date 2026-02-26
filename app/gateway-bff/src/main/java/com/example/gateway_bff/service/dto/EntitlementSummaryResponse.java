/*
 * どこで: Gateway-BFF 下流 DTO
 * 何を: entitlement 一覧の要素を表現する
 * なぜ: entitlement 応答を型安全に扱うため
 */
package com.example.gateway_bff.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EntitlementSummaryResponse(
    String stockKeepingUnit, String status, long version, String updatedAt) {}
