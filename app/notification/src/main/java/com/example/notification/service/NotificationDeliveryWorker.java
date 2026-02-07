/*
 * どこで: Notification 配信ワーカー
 * 何を: スケジュールで配信処理を起動する
 * なぜ: PENDING 通知を一定間隔で処理するため
 */
package com.example.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "notification.delivery.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NotificationDeliveryWorker {

  private final NotificationDeliveryService deliveryService;

  @Scheduled(fixedDelayString = "${notification.delivery.poll-interval}")
  public void run() {
    deliveryService.processPendingBatch();
  }
}
