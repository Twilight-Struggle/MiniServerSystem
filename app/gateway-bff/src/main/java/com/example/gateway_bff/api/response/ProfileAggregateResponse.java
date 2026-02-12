/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/api/response/ProfileAggregateResponse.java
 * 何を: BFF 集約 API の出力 DTO
 * なぜ: Account/Entitlement/Matchmaking の返却を 1 つの契約に統合するため
 */
package com.example.gateway_bff.api.response;

import java.util.Map;

public record ProfileAggregateResponse(
        Map<String, Object> account,
        Map<String, Object> entitlement,
        Map<String, Object> matchmaking) {
}
