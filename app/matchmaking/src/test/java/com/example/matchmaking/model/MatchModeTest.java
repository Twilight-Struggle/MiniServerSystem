package com.example.matchmaking.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MatchModeTest {

  @Test
  void fromValueParsesCasual() {
    assertThat(MatchMode.fromValue("casual")).isEqualTo(MatchMode.CASUAL);
    assertThat(MatchMode.fromValue("CASUAL")).isEqualTo(MatchMode.CASUAL);
  }

  @Test
  void fromValueParsesRank() {
    assertThat(MatchMode.fromValue("rank")).isEqualTo(MatchMode.RANK);
  }

  @Test
  void valueReturnsCanonicalMode() {
    assertThat(MatchMode.CASUAL.value()).isEqualTo("casual");
    assertThat(MatchMode.RANK.value()).isEqualTo("rank");
  }

  @Test
  void fromValueThrowsWhenUnsupported() {
    assertThatThrownBy(() -> MatchMode.fromValue("arcade"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported mode");
  }
}
