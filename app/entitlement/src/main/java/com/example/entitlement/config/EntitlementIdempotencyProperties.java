/*
 * どこで: Entitlement アプリの設定バインド
 * 何を: Idempotency Key の TTL 設定を保持する
 * なぜ: 冪等性レコードの保持期間を運用で調整できるようにするため
 */
package com.example.entitlement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "entitlement.idempotency")
public record EntitlementIdempotencyProperties(long ttlHours) {}
