/*
 * どこで: Notification テスト
 * 何を: Testcontainers Postgres の起動と接続をスモーク確認する
 * なぜ: コンテキストキャッシュが有効でも DB 接続が確立できることを保証するため
 */
package com.example.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PostgresContainerSmokeTest extends AbstractPostgresContainerTest {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void postgresContainerIsReachable() {
    // current_database() は数値を使わずに接続確認できるため、マジックナンバーを避ける
    final String databaseName =
        jdbcTemplate.getJdbcTemplate().queryForObject("SELECT current_database()", String.class);

    // DB 名は固定せず、接続できていることだけを確認する
    assertThat(databaseName).isNotBlank();
  }
}
