/*
 * どこで: Gateway-BFF サービス層テスト
 * 何を: SLO 用カスタムメトリクスが期待どおり記録されることを検証する
 * なぜ: メトリクス名やタグの退行を防ぎ、監視クエリの互換性を保つため
 */
package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GatewayMetricsTest {

  @Test
  void recordsLoginAndAccountIntegrationMetrics() {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final GatewayMetrics metrics = new GatewayMetrics(registry);

    metrics.recordLoginResult("redirect");
    metrics.recordLoginResult("error");
    metrics.recordAccountIntegrationError("ACCOUNT_TIMEOUT");
    metrics.recordAccountIntegrationError("ACCOUNT_BAD_GATEWAY");

    final Counter loginRedirect =
        registry.get("gateway.login.total").tag("result", "redirect").counter();
    final Counter loginError = registry.get("gateway.login.total").tag("result", "error").counter();
    final Counter timeout =
        registry
            .get("gateway.account.integration.error.total")
            .tag("code", "ACCOUNT_TIMEOUT")
            .counter();
    final Counter badGateway =
        registry
            .get("gateway.account.integration.error.total")
            .tag("code", "ACCOUNT_BAD_GATEWAY")
            .counter();

    assertThat(loginRedirect.count()).isEqualTo(1.0d);
    assertThat(loginError.count()).isEqualTo(1.0d);
    assertThat(timeout.count()).isEqualTo(1.0d);
    assertThat(badGateway.count()).isEqualTo(1.0d);
  }

  @Test
  void recordsProfileAggregateMetrics() {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final GatewayMetrics metrics = new GatewayMetrics(registry);

    metrics.recordProfileAggregateResult("success", "with_ticket");
    metrics.recordProfileAggregateResult("error", "without_ticket");
    metrics.recordProfileAggregateDependencyError("entitlement", "ENTITLEMENT_TIMEOUT");
    metrics.recordProfileAggregateDependencyDuration("account", "success", Duration.ofMillis(50));

    final Counter profileSuccess =
        registry
            .get("gateway.profile.aggregate.total")
            .tags("result", "success", "ticket", "with_ticket")
            .counter();
    final Counter profileError =
        registry
            .get("gateway.profile.aggregate.total")
            .tags("result", "error", "ticket", "without_ticket")
            .counter();
    final Counter dependencyError =
        registry
            .get("gateway.profile.aggregate.dependency.error.total")
            .tags("dependency", "entitlement", "reason", "ENTITLEMENT_TIMEOUT")
            .counter();
    final Timer dependencyDuration =
        registry
            .get("gateway.profile.aggregate.dependency.duration")
            .tags("dependency", "account", "result", "success")
            .timer();

    assertThat(profileSuccess.count()).isEqualTo(1.0d);
    assertThat(profileError.count()).isEqualTo(1.0d);
    assertThat(dependencyError.count()).isEqualTo(1.0d);
    assertThat(dependencyDuration.count()).isEqualTo(1L);
  }
}
