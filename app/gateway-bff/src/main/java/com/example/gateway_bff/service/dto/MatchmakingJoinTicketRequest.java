/*
 * どこで: Gateway-BFF 下流 DTO
 * 何を: matchmaking Join API 呼び出しボディを表現する
 * なぜ: 外部公開 DTO と下流契約を疎結合にするため
 */
package com.example.gateway_bff.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingJoinTicketRequest(
    Integer partySize, Map<String, Object> attributes, String idempotencyKey) {}
