/*
 * どこで: Entitlement API
 * 何を: ユーザ権利一覧のレスポンスを表す
 * なぜ: user_id と entitlements を明示的に返すため
 */
package com.example.entitlement.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EntitlementsResponse(
        String userId,
        List<EntitlementSummary> entitlements) {
}
