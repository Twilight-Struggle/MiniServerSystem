/*
 * どこで: Notification サービス層
 * 何を: 通知送信を模擬する実装
 * なぜ: 外部送信を伴わずに状態遷移を確認するため
 */
package com.example.notification.service;

import com.example.notification.model.NotificationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalNotificationSender implements NotificationSender {

    private static final Logger logger = LoggerFactory.getLogger(LocalNotificationSender.class);

    @Override
    public void send(NotificationRecord record) {
        // 実送信は行わず、ログに残すだけとする
        logger.info("notification simulated send id={} eventId={}", record.notificationId(), record.eventId());
    }
}
