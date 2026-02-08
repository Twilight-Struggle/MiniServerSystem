/*
 * どこで: Entitlement retention ワーカー
 * 何を: retention cleanup をスケジュールで起動する
 * なぜ: 手動介入なしで期限切れ削除を回すため
 */
package com.example.entitlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "entitlement.retention.enabled", havingValue = "true")
public class EntitlementRetentionWorker {

  private final EntitlementRetentionService retentionService;

  @Scheduled(fixedDelayString = "${entitlement.retention.cleanup-interval}")
  public void run() {
    // 直前の処理が終わってから次を待つ固定遅延で安全に回す。
    retentionService.cleanup();
  }
}
