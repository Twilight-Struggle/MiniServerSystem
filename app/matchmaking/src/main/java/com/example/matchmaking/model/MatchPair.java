/*
 * どこで: Matchmaking ドメインモデル
 * 何を: 1 回のマッチ成立結果を表現する
 * なぜ: Worker から Publisher に必要最小限の情報を渡すため
 */
package com.example.matchmaking.model;

import java.time.Instant;

public record MatchPair(String matchId, String ticketId1, String ticketId2, Instant matchedAt) {}
