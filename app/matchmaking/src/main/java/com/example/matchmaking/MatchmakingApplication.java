/*
 * どこで: Matchmaking アプリのエントリポイント
 * 何を: Spring Boot の起動と設定スキャン/スケジューラ有効化を行う
 * なぜ: API + Worker + 外部接続設定を単一アプリとして起動するため
 */
package com.example.matchmaking;

import com.example.common.config.TimeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@Import(TimeConfig.class)
public class MatchmakingApplication {

  public static void main(String[] args) {
    SpringApplication.run(MatchmakingApplication.class, args);
  }
}
