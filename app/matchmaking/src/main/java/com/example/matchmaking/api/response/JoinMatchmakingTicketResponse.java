/*
 * どこで: Matchmaking API レスポンス DTO
 * 何を: Join API の成功応答を定義する
 * なぜ: クライアントへ返却する初期 ticket 情報を固定するため
 */
package com.example.matchmaking.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record JoinMatchmakingTicketResponse(String ticketId, String status, String expiresAt) {}
