/*
 * どこで: Gateway-BFF API DTO
 * 何を: MATCHED 状態時の追加情報を返す
 * なぜ: 状態別で必要なレスポンス項目を明示するため
 */
package com.example.gateway_bff.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingMatchedPayloadResponse(
    String matchId, List<String> peerUserIds, Map<String, Object> session) {}
