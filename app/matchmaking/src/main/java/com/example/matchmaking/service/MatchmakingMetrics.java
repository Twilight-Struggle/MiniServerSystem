package com.example.matchmaking.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MatchmakingMetrics {

  private final MeterRegistry meterRegistry;
  private final Timer timeToMatchTimer;
  private final ConcurrentMap<String, AtomicLong> queueDepth = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicLong> oldestAge = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Counter> matchResultCounters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Counter> dependencyErrorCounters = new ConcurrentHashMap<>();

  public MatchmakingMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.timeToMatchTimer =
        Timer.builder("mm.time_to_match")
            .description("Time from ticket creation to match completion")
            .register(meterRegistry);
  }

  public void updateQueueDepth(String mode, long depth) {
    final AtomicLong value = queueDepth.computeIfAbsent(mode, this::registerQueueDepthGauge);
    value.set(Math.max(0, depth));
  }

  public void updateOldestQueueAge(String mode, long ageSeconds) {
    final AtomicLong value = oldestAge.computeIfAbsent(mode, this::registerOldestAgeGauge);
    value.set(Math.max(0, ageSeconds));
  }

  public void recordMatchResult(String result) {
    matchResultCounters.computeIfAbsent(result, this::registerMatchResultCounter).increment();
  }

  public void recordTimeToMatchSeconds(long seconds) {
    if (seconds < 0) {
      return;
    }
    timeToMatchTimer.record(Duration.ofSeconds(seconds));
  }

  public void recordDependencyError(String errorType) {
    dependencyErrorCounters
        .computeIfAbsent(errorType, this::registerDependencyErrorCounter)
        .increment();
  }

  private AtomicLong registerQueueDepthGauge(String mode) {
    final AtomicLong value = new AtomicLong(0);
    Gauge.builder("mm.queue.depth", value, AtomicLong::get)
        .tags(Tags.of("mode", mode))
        .register(meterRegistry);
    return value;
  }

  private AtomicLong registerOldestAgeGauge(String mode) {
    final AtomicLong value = new AtomicLong(0);
    Gauge.builder("mm.queue.oldest_age", value, AtomicLong::get)
        .tags(Tags.of("mode", mode))
        .register(meterRegistry);
    return value;
  }

  private Counter registerMatchResultCounter(String result) {
    return Counter.builder("mm.match.total")
        .tags(Tags.of("result", result))
        .register(meterRegistry);
  }

  private Counter registerDependencyErrorCounter(String errorType) {
    return Counter.builder("mm.dependency.error.total")
        .tags(Tags.of("type", errorType))
        .register(meterRegistry);
  }
}
