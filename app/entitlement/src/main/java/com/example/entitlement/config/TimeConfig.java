/*
 * どこで: Entitlement アプリの共通設定
 * 何を: Clock を DI 可能にする
 * なぜ: テストで時刻固定を容易にするため
 */
package com.example.entitlement.config;

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
