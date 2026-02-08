/*
 * どこで: Entitlement アプリの起動テスト
 * 何を: Spring コンテキストの起動を確認する
 * なぜ: 基本の構成が壊れていないことを保証するため
 */
package com.example.entitlement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EntitlementApplicationTests extends AbstractPostgresContainerTest {

  @Test
  void contextLoads() {}
}
