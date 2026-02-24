/*
 * どこで: Gateway-BFF 下流 DTO
 * 何を: matchmaking Join API 呼び出しボディを表現する
 * なぜ: 外部公開 DTO と下流契約を疎結合にするため
 */
package com.example.gateway_bff.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "下流連携 DTO record はデータ受け渡し専用のため")
public record MatchmakingJoinTicketRequest(
    Integer partySize, Map<String, Object> attributes, String idempotencyKey) {}
