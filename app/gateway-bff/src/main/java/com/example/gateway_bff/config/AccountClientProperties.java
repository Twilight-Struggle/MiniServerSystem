package com.example.gateway_bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "account")
public record AccountClientProperties(
    String baseUrl,
    String internalApiToken,
    String internalApiHeaderName,
    String resolveIdentityPath) {

  public AccountClientProperties {
    baseUrl = baseUrl == null ? "http://account:80" : baseUrl;
    internalApiToken = internalApiToken == null ? "" : internalApiToken;
    internalApiHeaderName =
        internalApiHeaderName == null || internalApiHeaderName.isBlank()
            ? "X-Internal-Token"
            : internalApiHeaderName;
    resolveIdentityPath =
        resolveIdentityPath == null || resolveIdentityPath.isBlank()
            ? "/identities:resolve"
            : resolveIdentityPath;
  }
}
