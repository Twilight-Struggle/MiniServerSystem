/*
 * どこで: Common 共通設定
 * 何を: Clock を DI 可能にする
 * なぜ: 各アプリで同一の時刻注入を使うため
 */
package com.example.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
