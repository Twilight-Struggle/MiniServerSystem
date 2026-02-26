/*
 * どこで: Matchmaking ドメインモデル
 * 何を: チケットの正規化された内部表現を定義する
 * なぜ: Repository と Service 間で受け渡す構造を固定するため
 */
package com.example.matchmaking.model;

import java.time.Instant;

public record TicketRecord(
    String ticketId,
    String userId,
    MatchMode mode,
    TicketStatus status,
    Instant createdAt,
    Instant expiresAt,
    String attributesJson,
    String matchId) {}
