/*
 * どこで: Gateway-BFF API DTO
 * 何を: ticket 状態問い合わせの応答を定義する
 * なぜ: MATCHED 情報を含む状態レスポンスを統一するため
 */
package com.example.gateway_bff.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingTicketStatusResponse(
    String ticketId, String status, String expiresAt, MatchmakingMatchedPayloadResponse matched) {}
