/*
 * どこで: Matchmaking API レスポンス DTO
 * 何を: Status API の応答を定義する
 * なぜ: status ごとに返しうる情報を共通構造で表現するため
 */
package com.example.matchmaking.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TicketStatusResponse(
    String ticketId, String status, String expiresAt, MatchedTicketPayload matched) {}
