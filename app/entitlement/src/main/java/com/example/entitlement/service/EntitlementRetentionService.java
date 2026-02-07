/*
 * どこで: Entitlement retention サービス
 * 何を: idempotency/outbox の期限切れデータを削除する
 * なぜ: テーブル肥大化を防ぎ、運用負荷を下げるため
 */
package com.example.entitlement.service;

import com.example.entitlement.config.EntitlementOutboxProperties;
import com.example.entitlement.repository.IdempotencyKeyRepository;
import com.example.entitlement.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntitlementRetentionService {

  private static final Logger logger = LoggerFactory.getLogger(EntitlementRetentionService.class);

  private final IdempotencyKeyRepository idempotencyKeyRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final EntitlementOutboxProperties outboxProperties;
  private final Clock clock;

  public void cleanup() {
    final Instant now = Instant.now(clock);
    // idempotency は expires_at に TTL が反映済みなので、期限切れのみ削除する。
    final int deletedIdempotency = idempotencyKeyRepository.deleteExpired(now);
    // outbox は publish 済みのみ対象にするため、published_at の TTL を使う。
    final Instant outboxThreshold = now.minus(outboxProperties.publishedTtl());
    final int deletedOutbox = outboxEventRepository.deletePublishedOlderThan(outboxThreshold);
    logger.info(
        "entitlement retention cleanup deleted idempotencyKeys={} outboxEvents={}"
            + " outboxThreshold={} idempotencyThreshold={}",
        deletedIdempotency,
        deletedOutbox,
        outboxThreshold,
        now);
  }
}
