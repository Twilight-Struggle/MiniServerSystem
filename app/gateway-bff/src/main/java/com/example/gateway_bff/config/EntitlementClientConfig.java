/*
 * どこで: Gateway-BFF 設定
 * 何を: entitlement 呼び出し専用 RestClient を提供する
 * なぜ: 下流サービスごとに baseUrl 設定責務を分離するため
 */
package com.example.gateway_bff.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(EntitlementClientProperties.class)
public class EntitlementClientConfig {

  @Bean
  RestClient entitlementRestClient(
      RestClient.Builder builder, EntitlementClientProperties properties) {
    return builder.baseUrl(properties.baseUrl()).build();
  }
}
