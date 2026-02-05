/*
 * どこで: Entitlement outbox ワーカー
 * 何を: スケジュールで outbox publish を起動する
 * なぜ: 定期的に未送信イベントを処理するため
 */
package com.example.entitlement.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "entitlement.outbox.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EntitlementOutboxWorker {

    private final EntitlementOutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${entitlement.outbox.poll-interval}")
    public void run() {
        publisher.publishPendingBatch();
    }
}
