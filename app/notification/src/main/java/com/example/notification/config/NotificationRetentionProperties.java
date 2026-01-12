/*
 * Where: Notification application configuration binding
 * What: Holds retention cleanup settings
 * Why: Keep retention policy and schedule tunable per environment
 */
package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.retention")
public record NotificationRetentionProperties(
        boolean enabled,
        int retentionDays,
        long cleanupIntervalMs
) {
}
