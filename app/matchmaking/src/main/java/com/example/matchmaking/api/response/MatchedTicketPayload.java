/*
 * どこで: Matchmaking API レスポンス DTO
 * 何を: MATCHED 時に付与する追加情報を表現する
 * なぜ: 状態に応じた可変項目を明確に分離するため
 */
package com.example.matchmaking.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchedTicketPayload(
    String matchId, List<String> peerUserIds, Map<String, Object> session) {}
