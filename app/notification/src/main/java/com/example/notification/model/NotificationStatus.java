/*
 * どこで: Notification ドメインモデル
 * 何を: 通知の状態を表す列挙
 * なぜ: DB と処理ロジックの状態を一致させるため
 */
package com.example.notification.model;

public enum NotificationStatus {
  PENDING,
  PROCESSING,
  SENT,
  FAILED
}
