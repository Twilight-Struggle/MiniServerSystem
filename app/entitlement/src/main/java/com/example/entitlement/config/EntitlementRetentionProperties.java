/*
 * どこで: Entitlement アプリの設定バインド
 * 何を: retention cleanup のスケジュール設定を保持する
 * なぜ: 削除間隔と有効/無効を運用で調整できるようにするため
 */
package com.example.entitlement.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "entitlement.retention")
public record EntitlementRetentionProperties(boolean enabled, Duration cleanupInterval) {}
