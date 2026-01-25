/*
 * どこで: Notification サービス層
 * 何を: 通知送信の抽象化インターフェース
 * なぜ: 実送信/テスト差し替えを容易にするため
 */
package com.example.notification.service;

import com.example.notification.model.NotificationRecord;

public interface NotificationSender {
    void send(NotificationRecord record);
}
