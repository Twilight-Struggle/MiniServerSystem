/*
 * どこで: Gateway-BFF API DTO
 * 何を: クライアント向け Join 入力を定義する
 * なぜ: API 契約を明示し後続でバリデーション追加しやすくするため
 */
package com.example.gateway_bff.api.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "JSON DTO record はシリアライズ用途であり、防御的コピーよりも契約互換性を優先するため")
public record MatchmakingJoinRequest(
    Integer partySize, Map<String, Object> attributes, String idempotencyKey) {}
