/*
 * どこで: Entitlement メトリクステスト
 * 何を: command/outbox 系メトリクスが記録されることを検証する
 * なぜ: SLO 指標の計測回帰を防ぐため
 */
package com.example.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EntitlementMetricsTest {

  @Test
  void recordsCommandAndOutboxMetrics() {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final EntitlementMetrics metrics = new EntitlementMetrics(registry);

    final Instant createdAt = Instant.parse("2026-02-24T00:00:00Z");
    final Instant publishedAt = Instant.parse("2026-02-24T00:00:05Z");

    metrics.recordCommand("GRANT", "success");
    metrics.recordOutboxBacklogAge(createdAt, publishedAt);
    metrics.recordOutboxPublishDelay(createdAt, publishedAt);
    metrics.updateOutboxFailedCurrent(3);

    final Counter command =
        registry
            .get("entitlement.command.total")
            .tag("action", "GRANT")
            .tag("result", "success")
            .counter();
    final Timer backlog = registry.get("entitlement.outbox.backlog.age").timer();
    final Timer delay = registry.get("entitlement.outbox.publish.delay").timer();
    final Gauge failed = registry.get("entitlement.outbox.failed.current").gauge();

    assertThat(command.count()).isEqualTo(1.0d);
    assertThat(backlog.count()).isEqualTo(1L);
    assertThat(delay.count()).isEqualTo(1L);
    assertThat(failed.value()).isEqualTo(3.0d);
  }
}
