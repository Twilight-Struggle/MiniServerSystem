/*
 * どこで: Notification ドメインモデル
 * 何を: notifications テーブルのスナップショット
 * なぜ: 配信処理とデバッグ API で共通化するため
 */
package com.example.notification.model;

import java.time.Instant;
import java.util.UUID;

public record NotificationRecord(
        UUID notificationId,
        UUID eventId,
        String userId,
        String type,
        Instant occurredAt,
        String payloadJson,
        NotificationStatus status,
        String lockedBy,
        Instant lockedAt,
        Instant leaseUntil,
        int attemptCount,
        Instant nextRetryAt,
        Instant createdAt,
        Instant sentAt) {
}
