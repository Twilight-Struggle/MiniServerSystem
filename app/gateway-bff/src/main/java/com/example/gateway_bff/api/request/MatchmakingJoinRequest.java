/*
 * どこで: Gateway-BFF API DTO
 * 何を: クライアント向け Join 入力を定義する
 * なぜ: API 契約を明示し後続でバリデーション追加しやすくするため
 */
package com.example.gateway_bff.api.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingJoinRequest(
    Integer partySize, Map<String, Object> attributes, String idempotencyKey) {}
