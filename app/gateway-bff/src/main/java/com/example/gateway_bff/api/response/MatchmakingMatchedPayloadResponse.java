/*
 * どこで: Gateway-BFF API DTO
 * 何を: MATCHED 状態時の追加情報を返す
 * なぜ: 状態別で必要なレスポンス項目を明示するため
 */
package com.example.gateway_bff.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "JSON DTO record はシリアライズ用途であり、防御的コピーよりも契約互換性を優先するため")
public record MatchmakingMatchedPayloadResponse(
    String matchId, List<String> peerUserIds, Map<String, Object> session) {}
