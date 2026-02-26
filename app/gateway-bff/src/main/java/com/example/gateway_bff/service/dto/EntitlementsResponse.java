/*
 * どこで: Gateway-BFF 下流 DTO
 * 何を: entitlement 一覧 API の応答を表現する
 * なぜ: user 単位の権利データを profile 集約に渡すため
 */
package com.example.gateway_bff.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EntitlementsResponse(String userId, List<EntitlementSummaryResponse> entitlements) {

  public EntitlementsResponse {
    entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
  }
}
