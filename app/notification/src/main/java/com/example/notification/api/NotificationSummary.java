/*
 * どこで: Notification API モデル
 * 何を: デバッグ用通知一覧の要素
 * なぜ: 送信状態と内容を確認できるようにするため
 */
package com.example.notification.api;

import com.example.notification.model.NotificationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NotificationSummary(
    UUID notificationId,
    UUID eventId,
    String type,
    NotificationStatus status,
    Instant createdAt,
    Instant sentAt,
    JsonNode payload) {}
