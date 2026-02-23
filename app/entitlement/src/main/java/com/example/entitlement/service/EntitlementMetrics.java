/*
 * どこで: Entitlement サービス層
 * 何を: SLO 監視向けのアプリ固有メトリクス記録を集約する
 * なぜ: API 成功率と outbox 遅延/失敗を運用で継続監視できるようにするため
 */
package com.example.entitlement.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "MeterRegistry は Spring 管理の共有コンポーネントで防御的コピーが不可能なため")
public class EntitlementMetrics {

  private static final String METRIC_COMMAND_TOTAL = "entitlement.command.total";
  private static final String METRIC_OUTBOX_PUBLISH_DELAY = "entitlement.outbox.publish.delay";
  private static final String METRIC_OUTBOX_BACKLOG_AGE = "entitlement.outbox.backlog.age";
  private static final String METRIC_OUTBOX_FAILED_CURRENT = "entitlement.outbox.failed.current";

  private final MeterRegistry meterRegistry;
  private final AtomicInteger outboxFailedCurrent = new AtomicInteger(0);
  private final ConcurrentMap<String, Counter> commandCounters = new ConcurrentHashMap<>();
  private final Timer outboxPublishDelayTimer;
  private final Timer outboxBacklogAgeTimer;

  public EntitlementMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    Gauge.builder(METRIC_OUTBOX_FAILED_CURRENT, outboxFailedCurrent, AtomicInteger::get)
        .description("Current number of FAILED outbox events")
        .register(meterRegistry);
    this.outboxPublishDelayTimer =
        Timer.builder(METRIC_OUTBOX_PUBLISH_DELAY)
            .description("Outbox publish delay from event creation to publish completion")
            .register(meterRegistry);
    this.outboxBacklogAgeTimer =
        Timer.builder(METRIC_OUTBOX_BACKLOG_AGE)
            .description("Outbox backlog age when an event is claimed by publisher")
            .register(meterRegistry);
  }

  public void recordCommand(String action, String result) {
    final String key = action + ":" + result;
    commandCounters
        .computeIfAbsent(
            key,
            ignored ->
                Counter.builder(METRIC_COMMAND_TOTAL)
                    .description("Entitlement command executions")
                    .tags(Tags.of("action", action, "result", result))
                    .register(meterRegistry))
        .increment();
  }

  public void recordOutboxPublishDelay(Instant createdAt, Instant publishedAt) {
    if (createdAt == null || publishedAt == null || publishedAt.isBefore(createdAt)) {
      return;
    }
    outboxPublishDelayTimer.record(Duration.between(createdAt, publishedAt));
  }

  public void recordOutboxBacklogAge(Instant createdAt, Instant observedAt) {
    if (createdAt == null || observedAt == null || observedAt.isBefore(createdAt)) {
      return;
    }
    outboxBacklogAgeTimer.record(Duration.between(createdAt, observedAt));
  }

  public void updateOutboxFailedCurrent(int failedCount) {
    outboxFailedCurrent.set(Math.max(failedCount, 0));
  }
}
