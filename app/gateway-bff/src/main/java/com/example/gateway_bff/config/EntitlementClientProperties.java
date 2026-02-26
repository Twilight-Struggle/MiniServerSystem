/*
 * どこで: Gateway-BFF 設定
 * 何を: entitlement サービス呼び出し設定を保持する
 * なぜ: BFF からの下流 URL とパスを外部化するため
 */
package com.example.gateway_bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "entitlement")
public record EntitlementClientProperties(String baseUrl, String getUserEntitlementsPath) {

  public EntitlementClientProperties {
    baseUrl = baseUrl == null ? "http://entitlement:80" : baseUrl;
    getUserEntitlementsPath =
        getUserEntitlementsPath == null || getUserEntitlementsPath.isBlank()
            ? "/v1/users/{userId}/entitlements"
            : getUserEntitlementsPath;
  }
}
