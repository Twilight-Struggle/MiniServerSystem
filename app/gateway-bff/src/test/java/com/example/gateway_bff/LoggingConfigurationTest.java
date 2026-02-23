/*
 * どこで: gateway-bff のログ設定テスト
 * 何を: JSON ログ設定と trace/span フィールド定義の存在を検証する
 * なぜ: 設定変更で構造化ログやトレース連携が欠落する回帰を防ぐため
 */
package com.example.gateway_bff;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LoggingConfigurationTest {

  @Test
  void logbackConfigurationContainsJsonAndTraceFields() throws IOException {
    final ClassPathResource resource = new ClassPathResource("logback-spring.xml");
    assertThat(resource.exists()).isTrue();

    final String configText =
        new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(configText).contains("LoggingEventCompositeJsonEncoder");
    assertThat(configText).contains("\"trace_id\":\"%X{trace_id:-%X{traceId:-}}\"");
    assertThat(configText).contains("\"span_id\":\"%X{span_id:-%X{spanId:-}}\"");
  }
}
