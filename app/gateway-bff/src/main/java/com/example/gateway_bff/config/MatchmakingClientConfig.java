/*
 * どこで: Gateway-BFF 設定
 * 何を: matchmaking 呼び出し専用 RestClient を提供する
 * なぜ: 下流サービスごとに baseUrl と設定責務を分離するため
 */
package com.example.gateway_bff.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MatchmakingClientProperties.class)
public class MatchmakingClientConfig {

  @Bean
  RestClient matchmakingRestClient(
      RestClient.Builder builder, MatchmakingClientProperties properties) {
    return builder.baseUrl(properties.baseUrl()).build();
  }
}
