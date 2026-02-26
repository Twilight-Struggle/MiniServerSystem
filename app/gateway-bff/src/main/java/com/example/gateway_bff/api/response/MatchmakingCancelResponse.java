/*
 * どこで: Gateway-BFF API DTO
 * 何を: Cancel API 応答を定義する
 * なぜ: DELETE の冪等応答を公開契約として固定するため
 */
package com.example.gateway_bff.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingCancelResponse(String ticketId, String status) {}
