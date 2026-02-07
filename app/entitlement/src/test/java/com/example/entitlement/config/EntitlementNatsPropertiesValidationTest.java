/*
 * どこで: Entitlement 設定バインドのバリデーションテスト
 * 何を: entitlement.nats の必須設定を検証する
 * なぜ: 起動時に設定不備を検知して publish 時の失敗を防ぐため
 */
package com.example.entitlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.context.annotation.Configuration;

class EntitlementNatsPropertiesValidationTest {

  // 必要最小の構成だけでプロパティ検証を走らせる
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

  @Test
  void contextFailsWhenSubjectIsMissing() {
    // subject 未設定は起動失敗にする
    contextRunner.run(assertValidationFailure("subject"));
  }

  @Test
  void contextFailsWhenSubjectIsBlank() {
    // 空白のみも NotBlank で弾く
    contextRunner
        .withPropertyValues("entitlement.nats.subject=   ")
        .run(assertValidationFailure("subject"));
  }

  @Test
  void contextFailsWhenStreamIsMissing() {
    // stream 未設定は起動失敗にする
    contextRunner
        .withPropertyValues(
            "entitlement.nats.subject=entitlement.events", "entitlement.nats.duplicate-window=2m")
        .run(assertValidationFailure("stream"));
  }

  @Test
  void contextFailsWhenStreamIsBlank() {
    // 空白のみも NotBlank で弾く
    contextRunner
        .withPropertyValues(
            "entitlement.nats.subject=entitlement.events",
            "entitlement.nats.stream=   ",
            "entitlement.nats.duplicate-window=2m")
        .run(assertValidationFailure("stream"));
  }

  @Test
  void contextFailsWhenDuplicateWindowIsMissing() {
    // duplicate-window 未設定は起動失敗にする
    contextRunner
        .withPropertyValues(
            "entitlement.nats.subject=entitlement.events",
            "entitlement.nats.stream=entitlement-events")
        .run(assertValidationFailure("duplicate"));
  }

  @Test
  void contextStartsWhenSubjectIsPresent() {
    // 正常値のときのみ起動成功する
    contextRunner
        .withPropertyValues(
            "entitlement.nats.subject=entitlement.events",
            "entitlement.nats.stream=entitlement-events",
            "entitlement.nats.duplicate-window=2m")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(EntitlementNatsProperties.class);
              final EntitlementNatsProperties properties =
                  context.getBean(EntitlementNatsProperties.class);
              assertThat(properties.subject()).isEqualTo("entitlement.events");
              assertThat(properties.stream()).isEqualTo("entitlement-events");
              assertThat(properties.duplicateWindow()).isNotNull();
            });
  }

  private ContextConsumer<AssertableApplicationContext> assertValidationFailure(
      String expectedField) {
    return context -> {
      assertThat(context).hasFailed();

      final Throwable failure = context.getStartupFailure();
      assertThat(failure).isNotNull();

      final Throwable root = org.assertj.core.util.Throwables.getRootCause(failure);
      assertThat(root).isInstanceOf(BindValidationException.class);

      assertThat(root.getMessage()).contains(expectedField);
    };
  }

  @Configuration
  @EnableConfigurationProperties(EntitlementNatsProperties.class)
  static class TestConfiguration {
    // ApplicationContextRunner 用の最小構成
  }
}
