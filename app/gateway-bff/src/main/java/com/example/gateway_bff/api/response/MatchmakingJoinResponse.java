/*
 * どこで: Gateway-BFF API DTO
 * 何を: Join API 応答を定義する
 * なぜ: BFF 公開契約を下流 DTO から分離するため
 */
package com.example.gateway_bff.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingJoinResponse(String ticketId, String status, String expiresAt) {}
