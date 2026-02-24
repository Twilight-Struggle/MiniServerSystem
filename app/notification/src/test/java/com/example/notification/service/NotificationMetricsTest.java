/*
 * どこで: Notification メトリクステスト
 * 何を: 配信結果/E2E遅延/backlog/DLQ メトリクスが記録されることを検証する
 * なぜ: Notification SLO 指標の計測回帰を防ぐため
 */
package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationMetricsTest {

  @Test
  void recordsDeliveryAndBacklogMetrics() {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final NotificationMetrics metrics = new NotificationMetrics(registry);

    final Instant occurredAt = Instant.parse("2026-02-24T00:00:00Z");
    final Instant sentAt = Instant.parse("2026-02-24T00:00:10Z");

    metrics.recordDeliveryResult("sent");
    metrics.recordDeliveryE2eDelay(occurredAt, sentAt);
    metrics.recordDlqMoved();
    metrics.updateBacklogCurrent(5);

    final Counter sent =
        registry.get("notification.delivery.total").tag("result", "sent").counter();
    final Timer e2e = registry.get("notification.delivery.e2e.delay").timer();
    final Counter dlq = registry.get("notification.dlq.total").counter();
    final Gauge backlog = registry.get("notification.backlog.current").gauge();

    assertThat(sent.count()).isEqualTo(1.0d);
    assertThat(e2e.count()).isEqualTo(1L);
    assertThat(dlq.count()).isEqualTo(1.0d);
    assertThat(backlog.value()).isEqualTo(5.0d);
  }
}
