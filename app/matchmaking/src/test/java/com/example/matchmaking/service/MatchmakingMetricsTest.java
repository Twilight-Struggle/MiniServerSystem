package com.example.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MatchmakingMetricsTest {

  @Test
  void updatesQueueMetricsAndCounters() {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final MatchmakingMetrics metrics = new MatchmakingMetrics(registry);

    metrics.updateQueueDepth("casual", 3);
    metrics.updateOldestQueueAge("casual", 9);
    metrics.recordMatchResult("matched");
    metrics.recordDependencyError("redis");
    metrics.recordTimeToMatchSeconds(5);

    final double depth = registry.get("mm.queue.depth").tag("mode", "casual").gauge().value();
    final double oldest = registry.get("mm.queue.oldest_age").tag("mode", "casual").gauge().value();
    final double matched =
        registry.get("mm.match.total").tag("result", "matched").counter().count();
    final double errors =
        registry.get("mm.dependency.error.total").tag("type", "redis").counter().count();
    final long timerCount = registry.get("mm.time_to_match").timer().count();

    assertThat(depth).isEqualTo(3.0);
    assertThat(oldest).isEqualTo(9.0);
    assertThat(matched).isEqualTo(1.0);
    assertThat(errors).isEqualTo(1.0);
    assertThat(timerCount).isEqualTo(1L);
  }

  @Test
  void ignoresNegativeTimeToMatch() {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final MatchmakingMetrics metrics = new MatchmakingMetrics(registry);

    metrics.recordTimeToMatchSeconds(-1);

    assertThat(registry.get("mm.time_to_match").timer().count()).isZero();
  }
}
