/*
 * どこで: Entitlement テスト基盤
 * 何を: Testcontainers(Postgres) と DataSource/Flyway の共通設定を提供する
 * なぜ: テストごとの重複設定を削減し、H2依存を排除するため
 */
package com.example.entitlement;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractPostgresContainerTest {

  // JVM 内のテスト全体で共通の Postgres コンテナを使い回し、起動コストを抑える
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    // Spring の @DynamicPropertySource は JUnit の Testcontainers 拡張より先に動く場合がある。
    // コンテキストキャッシュで接続情報が使い回されても DB が必ず起動済みになるよう、ここで明示起動する。
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);

    registry.add("spring.datasource.hikari.schema", () -> "entitlement");

    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.flyway.default-schema", () -> "entitlement");
    registry.add("spring.flyway.schemas", () -> "entitlement");
    registry.add("spring.flyway.create-schemas", () -> "true");
    registry.add("spring.flyway.table", () -> "flyway_schema_history_entitlement");
  }
}
