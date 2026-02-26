/*
 * どこで: Gateway-BFF サービス層
 * 何を: SLO 監視向けにログイン導線と account 連携エラーのメトリクスを記録する
 * なぜ: /login 成功率と ACCOUNT_* エラー増加を Prometheus から直接観測できるようにするため
 */
package com.example.gateway_bff.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "MeterRegistry は Spring 管理の共有コンポーネントで防御的コピーが不可能なため")
public class GatewayMetrics {

  private static final String METRIC_LOGIN_TOTAL = "gateway.login.total";
  private static final String METRIC_ACCOUNT_INTEGRATION_ERROR_TOTAL =
      "gateway.account.integration.error.total";
  private static final String METRIC_PROFILE_AGGREGATE_TOTAL = "gateway.profile.aggregate.total";
  private static final String METRIC_PROFILE_AGGREGATE_DEPENDENCY_ERROR_TOTAL =
      "gateway.profile.aggregate.dependency.error.total";
  private static final String METRIC_PROFILE_AGGREGATE_DEPENDENCY_DURATION =
      "gateway.profile.aggregate.dependency.duration";

  private final MeterRegistry meterRegistry;
  private final ConcurrentMap<String, Counter> loginCounters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Counter> accountErrorCounters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Counter> profileAggregateCounters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Counter> profileAggregateDependencyErrorCounters =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Timer> profileAggregateDependencyTimers =
      new ConcurrentHashMap<>();

  public GatewayMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordLoginResult(String result) {
    loginCounters
        .computeIfAbsent(
            result,
            ignored ->
                Counter.builder(METRIC_LOGIN_TOTAL)
                    .description("Gateway login endpoint outcomes")
                    .tags(Tags.of("result", result))
                    .register(meterRegistry))
        .increment();
  }

  public void recordAccountIntegrationError(String code) {
    accountErrorCounters
        .computeIfAbsent(
            code,
            ignored ->
                Counter.builder(METRIC_ACCOUNT_INTEGRATION_ERROR_TOTAL)
                    .description("Gateway account integration errors by code")
                    .tags(Tags.of("code", code))
                    .register(meterRegistry))
        .increment();
  }

  public void recordProfileAggregateResult(String result, String ticket) {
    final String key = result + "|" + ticket;
    profileAggregateCounters
        .computeIfAbsent(
            key,
            ignored ->
                Counter.builder(METRIC_PROFILE_AGGREGATE_TOTAL)
                    .description("Gateway profile aggregate outcomes")
                    .tags(Tags.of("result", result, "ticket", ticket))
                    .register(meterRegistry))
        .increment();
  }

  public void recordProfileAggregateDependencyError(String dependency, String reason) {
    final String key = dependency + "|" + reason;
    profileAggregateDependencyErrorCounters
        .computeIfAbsent(
            key,
            ignored ->
                Counter.builder(METRIC_PROFILE_AGGREGATE_DEPENDENCY_ERROR_TOTAL)
                    .description("Gateway profile aggregate dependency errors")
                    .tags(Tags.of("dependency", dependency, "reason", reason))
                    .register(meterRegistry))
        .increment();
  }

  public void recordProfileAggregateDependencyDuration(
      String dependency, String result, Duration duration) {
    final String key = dependency + "|" + result;
    profileAggregateDependencyTimers
        .computeIfAbsent(
            key,
            ignored ->
                Timer.builder(METRIC_PROFILE_AGGREGATE_DEPENDENCY_DURATION)
                    .description("Gateway profile aggregate dependency call duration")
                    .tags(Tags.of("dependency", dependency, "result", result))
                    .register(meterRegistry))
        .record(duration);
  }
}
