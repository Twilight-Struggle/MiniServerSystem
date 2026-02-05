/*
 * どこで: Notification アプリのスモークテスト
 * 何を: Spring コンテキストの起動を確認する
 * なぜ: 主要な構成が破壊されていないことを担保するため
 */
package com.example.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationApplicationTests extends AbstractPostgresContainerTest {

  @Test
  void contextLoads() {}
}
