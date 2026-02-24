/*
 * どこで: Matchmaking API リクエスト DTO
 * 何を: Join API の入力を定義する
 * なぜ: 受信 JSON を型安全に取り扱うため
 */
package com.example.matchmaking.api.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record JoinMatchmakingTicketRequest(
    @NotNull Integer partySize, Map<String, Object> attributes, @NotBlank String idempotencyKey) {}
