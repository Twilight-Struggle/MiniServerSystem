/*
 * Where: Notification cleanup worker
 * What: Triggers retention cleanup on a schedule
 * Why: Automate deletion without manual intervention
 */
package com.example.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.retention.enabled", havingValue = "true")
public class NotificationRetentionWorker {

  private final NotificationRetentionService retentionService;

  @Scheduled(fixedDelayString = "${notification.retention.cleanup-interval}")
  public void run() {
    retentionService.cleanup();
  }
}
