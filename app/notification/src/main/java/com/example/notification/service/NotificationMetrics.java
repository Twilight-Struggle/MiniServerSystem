/*
 * どこで: Notification サービス層
 * 何を: 配信成功率/E2E遅延/backlog/DLQ のアプリ固有メトリクスを記録する
 * なぜ: SLO で定義した非同期指標を Prometheus から直接観測できるようにするため
 */
package com.example.notification.service;

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
public class NotificationMetrics {

  private static final String METRIC_DELIVERY_TOTAL = "notification.delivery.total";
  private static final String METRIC_DELIVERY_E2E_DELAY = "notification.delivery.e2e.delay";
  private static final String METRIC_BACKLOG_CURRENT = "notification.backlog.current";
  private static final String METRIC_DLQ_TOTAL = "notification.dlq.total";

  private final MeterRegistry meterRegistry;
  private final AtomicInteger backlogCurrent = new AtomicInteger(0);
  private final ConcurrentMap<String, Counter> deliveryCounters = new ConcurrentHashMap<>();
  private final Counter dlqCounter;
  private final Timer deliveryE2eDelayTimer;

  public NotificationMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    Gauge.builder(METRIC_BACKLOG_CURRENT, backlogCurrent, AtomicInteger::get)
        .description("Current number of pending notifications")
        .register(meterRegistry);
    this.dlqCounter =
        Counter.builder(METRIC_DLQ_TOTAL)
            .description("Total number of notifications moved to DLQ")
            .register(meterRegistry);
    this.deliveryE2eDelayTimer =
        Timer.builder(METRIC_DELIVERY_E2E_DELAY)
            .description("End-to-end delivery delay from event occurred_at to sent_at")
            .register(meterRegistry);
  }

  public void recordDeliveryResult(String result) {
    deliveryCounters
        .computeIfAbsent(
            result,
            ignored ->
                Counter.builder(METRIC_DELIVERY_TOTAL)
                    .description("Notification delivery outcomes")
                    .tags(Tags.of("result", result))
                    .register(meterRegistry))
        .increment();
  }

  public void recordDeliveryE2eDelay(Instant occurredAt, Instant sentAt) {
    if (occurredAt == null || sentAt == null || sentAt.isBefore(occurredAt)) {
      return;
    }
    deliveryE2eDelayTimer.record(Duration.between(occurredAt, sentAt));
  }

  public void recordDlqMoved() {
    dlqCounter.increment();
  }

  public void updateBacklogCurrent(int backlogCount) {
    backlogCurrent.set(Math.max(backlogCount, 0));
  }
}
