package com.example.matchmaking.model;

import java.time.Instant;

public record MatchPair(
    String matchId, MatchMode mode, String ticketId1, String ticketId2, Instant matchedAt) {}
