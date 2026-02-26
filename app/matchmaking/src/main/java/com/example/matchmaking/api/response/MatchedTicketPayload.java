/*
 * どこで: Matchmaking API レスポンス DTO
 * 何を: MATCHED 時に付与する追加情報を表現する
 * なぜ: 状態に応じた可変項目を明確に分離するため
 */
package com.example.matchmaking.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "API DTO record はレスポンス整形用途のため")
public record MatchedTicketPayload(
    String matchId, List<String> peerUserIds, Map<String, Object> session) {}
