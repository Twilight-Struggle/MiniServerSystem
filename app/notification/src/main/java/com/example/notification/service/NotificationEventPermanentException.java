/*
 * どこで: Notification サービス層
 * 何を: 恒久的なイベント処理失敗を示す例外
 * なぜ: NATS 再配信を止めて破棄する判断に使うため
 */
package com.example.notification.service;

public class NotificationEventPermanentException extends RuntimeException {

    public NotificationEventPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
