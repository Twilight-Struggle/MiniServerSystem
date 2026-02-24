/*
 * どこで: Gateway-BFF 下流 DTO
 * 何を: matchmaking Status(MATCHED) の追加情報を表現する
 * なぜ: 下流応答の可変項目を明示するため
 */
package com.example.gateway_bff.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingMatchedPayload(
    String matchId, List<String> peerUserIds, Map<String, Object> session) {}
