/*
 * どこで: Gateway-BFF 下流 DTO
 * 何を: matchmaking Join API の応答を表現する
 * なぜ: 下流スキーマ差分を API 層へ伝播させないため
 */
package com.example.gateway_bff.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingJoinTicketResponse(String ticketId, String status, String expiresAt) {}
