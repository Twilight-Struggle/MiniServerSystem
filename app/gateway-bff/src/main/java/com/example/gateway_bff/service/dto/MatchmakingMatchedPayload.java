/*
 * どこで: Gateway-BFF 下流 DTO
 * 何を: matchmaking Status(MATCHED) の追加情報を表現する
 * なぜ: 下流応答の可変項目を明示するため
 */
package com.example.gateway_bff.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "下流連携 DTO record はデータ受け渡し専用のため")
public record MatchmakingMatchedPayload(
    String matchId, List<String> peerUserIds, Map<String, Object> session) {}
