/*
 * どこで: Gateway-BFF 下流 DTO
 * 何を: matchmaking Cancel API の応答を表現する
 * なぜ: DELETE の冪等結果を型として扱うため
 */
package com.example.gateway_bff.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingCancelTicketResponse(String ticketId, String status) {}
