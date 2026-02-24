package com.example.matchmaking.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MatchPairTest {

  @Test
  void recordStoresValues() {
    final Instant now = Instant.parse("2026-02-24T12:00:00Z");
    final MatchPair pair = new MatchPair("match-1", MatchMode.CASUAL, "ticket-1", "ticket-2", now);

    assertThat(pair.matchId()).isEqualTo("match-1");
    assertThat(pair.mode()).isEqualTo(MatchMode.CASUAL);
    assertThat(pair.ticketId1()).isEqualTo("ticket-1");
    assertThat(pair.ticketId2()).isEqualTo("ticket-2");
    assertThat(pair.matchedAt()).isEqualTo(now);
  }
}
