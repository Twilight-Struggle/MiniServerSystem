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

import lombok.RequiredArgsConstructor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
        Instant now = Instant.now(clock);
        String lockedBy = resolveLockedBy();
        Instant leaseUntil = now.plus(properties.lease());
        // claim を単一 SQL で行い、送信 IO を長期トランザクションに載せない
        List<NotificationRecord> pending = notificationRepository.claimPendingForUpdate(
                properties.batchSize(),
                now,
                leaseUntil,
                lockedBy);
        for (NotificationRecord record : pending) {
            try {
                sender.send(record);
                int updated = notificationRepository.markSent(record.notificationId(), now, lockedBy);
                if (updated == 0) {
                    logger.warn("notification sent but lock was lost id={} eventId={}",
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
        int nextAttempt = record.attemptCount() + 1;
        if (nextAttempt >= properties.maxAttempts()) {
            boolean moved = moveToDlqAndMarkFailed(record, nextAttempt, ex, now, lockedBy);
            if (!moved) {
                logger.warn("notification dlq skipped because lock was lost id={} eventId={}",
                        record.notificationId(),
                        record.eventId());
                return;
            }
            logger.warn("notification moved to DLQ id={} eventId={}", record.notificationId(), record.eventId(), ex);
            return;
        }
        Duration backoff = computeBackoffDuration(nextAttempt);
        Instant nextRetryAt = now.plus(backoff);
        int updated = notificationRepository.markRetry(record.notificationId(), nextAttempt, nextRetryAt, false,
                lockedBy);
        if (updated == 0) {
            logger.warn("notification retry skipped because lock was lost id={} attempt={}",
                    record.notificationId(),
                    nextAttempt);
        }
        logger.warn("notification retry scheduled id={} attempt={}", record.notificationId(), nextAttempt, ex);
    }

    private boolean moveToDlqAndMarkFailed(NotificationRecord record,
            int nextAttempt,
            RuntimeException ex,
            Instant now,
            String lockedBy) {
        // DLQ 登録と FAILED 更新を同一トランザクションにまとめ、ロック喪失時の不整合を避ける
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        boolean updated = transactionTemplate.execute(status -> {
            notificationDlqRepository.insert(
                    UUID.randomUUID(),
                    record.notificationId(),
                    record.eventId(),
                    record.payloadJson(),
                    truncateError(ex.getMessage()),
                    now);
            int count = notificationRepository.markRetry(record.notificationId(), nextAttempt, null, true, lockedBy);
            if (count == 0) {
                status.setRollbackOnly();
                return false;
            }
            return true;
        });
        return updated;
    }

    @VisibleForTesting
    Duration computeBackoffDuration(int attempt) {
        double baseMillis = properties.backoffBase().toMillis();
        double exp = baseMillis * Math.pow(properties.backoffExponentBase(), (attempt - 1));
        double capped = Math.min(exp, properties.backoffMax().toMillis());
        double jitterMin = properties.backoffJitterMin();
        double jitterMax = properties.backoffJitterMax();
        double jitter = jitterMin + ThreadLocalRandom.current().nextDouble() * (jitterMax - jitterMin);
        long backoffMillis = (long) Math.ceil(capped * jitter);
        long minMillis = properties.backoffMin().toMillis();
        return Duration.ofMillis(Math.max(minMillis, backoffMillis));
    }

    private String truncateError(String message) {
        if (message == null) {
            return "unknown error";
        }
        int maxLength = properties.errorMessageMaxLength();
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength);
    }

    @VisibleForTesting
    String resolveLockedBy() {
        String env = System.getenv(HOSTNAME_ENV);
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
