/*
 * どこで: Matchmaking API レスポンス DTO
 * 何を: Cancel API の応答を定義する
 * なぜ: DELETE を冪等に扱う契約を明示するため
 */
package com.example.matchmaking.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CancelMatchmakingTicketResponse(String ticketId, String status) {}
