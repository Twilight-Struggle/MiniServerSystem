package com.example.gateway_bff.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AccountClientProperties.class)
public class AccountClientConfig {

  @Bean
  RestClient accountRestClient(RestClient.Builder builder, AccountClientProperties properties) {
    // account service 呼び出し専用 RestClient。
    return builder.baseUrl(properties.baseUrl()).build();
  }
}
