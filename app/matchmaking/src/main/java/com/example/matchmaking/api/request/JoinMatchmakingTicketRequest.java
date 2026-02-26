/*
 * どこで: Matchmaking API リクエスト DTO
 * 何を: Join API の入力を定義する
 * なぜ: 受信 JSON を型安全に取り扱うため
 */
package com.example.matchmaking.api.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "API DTO record はリクエスト受け取り専用であり、防御的コピーを行わないため")
public record JoinMatchmakingTicketRequest(
    @NotNull Integer partySize, Map<String, Object> attributes, @NotBlank String idempotencyKey) {}
