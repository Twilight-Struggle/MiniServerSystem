/*
 * どこで: Notification サービス層
 * 何を: PENDING 通知の送信とリトライ/DLQ を処理する
 * なぜ: 通知の最終状態を制御し運用介入を可能にするため
 */
package com.example.notification.service;

import com.example.notification.config.NotificationDeliveryProperties;
import com.example.notification.model.NotificationRecord;
import com.example.notification.repository.NotificationDlqRepository;
import com.example.notification.repository.NotificationRepository;
import com.google.common.annotations.VisibleForTesting;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

  private static final Logger logger = LoggerFactory.getLogger(NotificationDeliveryService.class);
  private static final String HOSTNAME_ENV = "HOSTNAME";
  private static final String DEFAULT_HOSTNAME = "unknown-host";

  private final NotificationRepository notificationRepository;
  private final NotificationDlqRepository notificationDlqRepository;
  private final NotificationSender sender;
  private final NotificationDeliveryProperties properties;
  private final Clock clock;
  private final PlatformTransactionManager transactionManager;

  public void processPendingBatch() {
    final Instant now = Instant.now(clock);
    final String lockedBy = resolveLockedBy();
    final Instant leaseUntil = now.plus(properties.lease());
    // claim を単一 SQL で行い、送信 IO を長期トランザクションに載せない
    final List<NotificationRecord> pending =
        notificationRepository.claimPendingForUpdate(
            properties.batchSize(), now, leaseUntil, lockedBy);
    for (final NotificationRecord record : pending) {
      try {
        sender.send(record);
        final int updated = notificationRepository.markSent(record.notificationId(), now, lockedBy);
        if (updated == 0) {
          logger.warn(
              "notification sent but lock was lost id={} eventId={}",
              record.notificationId(),
              record.eventId());
        }
      } catch (DataAccessException ex) {
        handleFailure(record, ex, now, lockedBy);
      } catch (RuntimeException ex) {
        handleFailure(record, ex, now, lockedBy);
      }
    }
  }

  @VisibleForTesting
  void handleFailure(NotificationRecord record, RuntimeException ex, Instant now, String lockedBy) {
    final int nextAttempt = record.attemptCount() + 1;
    if (nextAttempt >= properties.maxAttempts()) {
      final boolean moved = moveToDlqAndMarkFailed(record, nextAttempt, ex, now, lockedBy);
      if (!moved) {
        logger.warn(
            "notification dlq skipped because lock was lost id={} eventId={}",
            record.notificationId(),
            record.eventId());
        return;
      }
      logger.warn(
          "notification moved to DLQ id={} eventId={}",
          record.notificationId(),
          record.eventId(),
          ex);
      return;
    }
    final Duration backoff = computeBackoffDuration(nextAttempt);
    final Instant nextRetryAt = now.plus(backoff);
    final int updated =
        notificationRepository.markRetry(
            record.notificationId(), nextAttempt, nextRetryAt, false, lockedBy);
    if (updated == 0) {
      logger.warn(
          "notification retry skipped because lock was lost id={} attempt={}",
          record.notificationId(),
          nextAttempt);
    }
    logger.warn(
        "notification retry scheduled id={} attempt={}", record.notificationId(), nextAttempt, ex);
  }

  private boolean moveToDlqAndMarkFailed(
      NotificationRecord record,
      int nextAttempt,
      RuntimeException ex,
      Instant now,
      String lockedBy) {
    // DLQ 登録と FAILED 更新を同一トランザクションにまとめ、ロック喪失時の不整合を避ける
    final Boolean updated =
        transactionTemplate()
            .execute(
                status -> {
                  notificationDlqRepository.insert(
                      UUID.randomUUID(),
                      record.notificationId(),
                      record.eventId(),
                      record.payloadJson(),
                      truncateError(ex.getMessage()),
                      now);
                  final int count =
                      notificationRepository.markRetry(
                          record.notificationId(), nextAttempt, null, true, lockedBy);
                  if (count == 0) {
                    status.setRollbackOnly();
                    return false;
                  }
                  return true;
                });
    return Boolean.TRUE.equals(updated);
  }

  @VisibleForTesting
  TransactionTemplate transactionTemplate() {
    // TransactionTemplate の戻りが null の場合もあるため、呼び出し側で安全に扱えるよう分離する
    return new TransactionTemplate(transactionManager);
  }

  @VisibleForTesting
  Duration computeBackoffDuration(int attempt) {
    final double baseMillis = properties.backoffBase().toMillis();
    final double exp = baseMillis * Math.pow(properties.backoffExponentBase(), (attempt - 1));
    final double capped = Math.min(exp, properties.backoffMax().toMillis());
    final double jitterMin = properties.backoffJitterMin();
    final double jitterMax = properties.backoffJitterMax();
    final double jitter =
        jitterMin + ThreadLocalRandom.current().nextDouble() * (jitterMax - jitterMin);
    final long backoffMillis = (long) Math.ceil(capped * jitter);
    final long minMillis = properties.backoffMin().toMillis();
    return Duration.ofMillis(Math.max(minMillis, backoffMillis));
  }

  private String truncateError(String message) {
    if (message == null) {
      return "unknown error";
    }
    final int maxLength = properties.errorMessageMaxLength();
    if (message.length() <= maxLength) {
      return message;
    }
    return message.substring(0, maxLength);
  }

  @VisibleForTesting
  String resolveLockedBy() {
    final String env = System.getenv(HOSTNAME_ENV);
    if (env != null && !env.isBlank()) {
      return env;
    }
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException | SecurityException ex) {
      logger.warn("failed to resolve hostname; fallback to {}", DEFAULT_HOSTNAME, ex);
      return DEFAULT_HOSTNAME;
    }
  }
}
