/*
 * どこで: Gateway-BFF 下流 DTO
 * 何を: matchmaking Status API の応答を表現する
 * なぜ: MATCHED 追加ペイロードを含むレスポンスを型で扱うため
 */
package com.example.gateway_bff.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingTicketStatusResponse(
    String ticketId, String status, String expiresAt, MatchmakingMatchedPayload matched) {}
