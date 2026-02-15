package com.example.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "account.internal-api")
public record AccountInternalApiProperties(String headerName, String token) {

  public AccountInternalApiProperties {
    headerName = headerName == null || headerName.isBlank() ? "X-Internal-Token" : headerName;
    token = token == null ? "" : token;
  }
}
