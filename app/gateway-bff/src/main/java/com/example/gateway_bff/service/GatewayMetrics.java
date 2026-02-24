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

  private final MeterRegistry meterRegistry;
  private final ConcurrentMap<String, Counter> loginCounters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Counter> accountErrorCounters = new ConcurrentHashMap<>();

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
}
