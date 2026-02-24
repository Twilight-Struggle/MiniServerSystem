/*
 * どこで: Notification 配信サービスのユニットテスト
 * 何を: リトライ計算・失敗処理・ロック識別の挙動を検証する
 * なぜ: 再送制御とDLQ分岐の安全性を担保するため
 */
package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.notification.config.NotificationDeliveryProperties;
import com.example.notification.model.NotificationRecord;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.NotificationDlqRepository;
import com.example.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-01-17T00:00:00Z");
  private static final String LOCKED_BY = "worker-1";
  private static final NotificationDeliveryProperties PROPERTIES =
      new NotificationDeliveryProperties(
          true,
          Duration.ofSeconds(1),
          50,
          10,
          Duration.ofSeconds(1),
          Duration.ofSeconds(60),
          2.0d,
          0.5d,
          1.5d,
          Duration.ofSeconds(1),
          1000,
          Duration.ofSeconds(30));

  @Mock private NotificationRepository notificationRepository;

  @Mock private NotificationDlqRepository notificationDlqRepository;

  @Mock private NotificationSender sender;
  @Mock private NotificationMetrics metrics;

  private NotificationDeliveryService service;

  @BeforeEach
  void setUp() {
    // 時刻に依存する処理が混ざってもテストが揺れないよう固定クロックを注入する
    final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    final PlatformTransactionManager transactionManager = new NoOpTransactionManager();
    service =
        new NotificationDeliveryService(
            notificationRepository,
            notificationDlqRepository,
            sender,
            PROPERTIES,
            metrics,
            clock,
            transactionManager);
  }

  @Test
  void computeBackoffDurationReturnsValueWithinRangeBeforeCap() {
    // cap 未到達時の指数バックオフ + ジッター範囲を検証する
    final int attempt = 3;
    final Duration min = minBackoffDuration(attempt);
    final Duration max = maxBackoffDuration(attempt);

    final Duration backoff = service.computeBackoffDuration(attempt);

    // 乱数ジッターが入っても範囲内に収まることが重要
    assertThat(backoff).isBetween(min, max);
  }

  @Test
  void computeBackoffDurationReturnsValueWithinRangeAfterCap() {
    // cap 到達後は指数値ではなく cap を基準にジッターが掛かることを検証する
    final int attempt = 7;
    final Duration min = minBackoffDuration(attempt);
    final Duration max = maxBackoffDuration(attempt);

    final Duration backoff = service.computeBackoffDuration(attempt);

    assertThat(backoff).isBetween(min, max);
  }

  @Test
  void handleFailureMovesToDlqWhenMaxAttemptsExceeded() {
    // maxAttempts 到達時に DLQ へ移送されることを確認する
    final NotificationRecord record = notificationRecord(PROPERTIES.maxAttempts() - 1);
    final RuntimeException failure = new IllegalStateException("boom");
    when(notificationRepository.markRetry(
            any(UUID.class), any(int.class), isNull(), eq(true), any()))
        .thenReturn(1);

    service.handleFailure(record, failure, FIXED_NOW, LOCKED_BY);

    // DLQ 側の登録内容を確認し、失敗理由が伝播することを保証する
    final ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<Instant> createdAtCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(notificationDlqRepository)
        .insert(
            any(UUID.class),
            eq(record.notificationId()),
            eq(record.eventId()),
            eq(record.payloadJson()),
            errorMessageCaptor.capture(),
            createdAtCaptor.capture());
    assertThat(errorMessageCaptor.getValue()).isEqualTo("boom");
    assertThat(createdAtCaptor.getValue()).isEqualTo(FIXED_NOW);

    // DLQ 移送時は FAILED 扱いで next_retry_at を NULL にする
    verify(notificationRepository)
        .markRetry(
            eq(record.notificationId()),
            eq(PROPERTIES.maxAttempts()),
            isNull(),
            eq(true),
            eq(LOCKED_BY));
    verifyNoInteractions(sender);
  }

  @Test
  void handleFailureTruncatesErrorMessageUsingConfiguredLimit() {
    // 設定した最大長でエラーメッセージが切り詰められることを検証する
    final int longMessageLength = PROPERTIES.errorMessageMaxLength() + 5;
    final String longMessage = "a".repeat(longMessageLength);
    final NotificationRecord record = notificationRecord(PROPERTIES.maxAttempts() - 1);
    when(notificationRepository.markRetry(
            any(UUID.class), any(int.class), isNull(), eq(true), any()))
        .thenReturn(1);

    service.handleFailure(record, new IllegalStateException(longMessage), FIXED_NOW, LOCKED_BY);

    final ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);
    verify(notificationDlqRepository)
        .insert(
            any(UUID.class),
            eq(record.notificationId()),
            eq(record.eventId()),
            eq(record.payloadJson()),
            errorMessageCaptor.capture(),
            eq(FIXED_NOW));
    assertThat(errorMessageCaptor.getValue())
        .isEqualTo(longMessage.substring(0, PROPERTIES.errorMessageMaxLength()));
  }

  @Test
  void handleFailureSchedulesRetryWhenAttemptsRemaining() {
    // retry 予定が立つ場合は next_retry_at がジッター範囲内になることを検証する
    final NotificationRecord record = notificationRecord(1);
    final RuntimeException failure = new IllegalStateException("boom");

    service.handleFailure(record, failure, FIXED_NOW, LOCKED_BY);

    final ArgumentCaptor<Instant> nextRetryAtCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(notificationRepository)
        .markRetry(
            eq(record.notificationId()),
            eq(2),
            nextRetryAtCaptor.capture(),
            eq(false),
            eq(LOCKED_BY));

    final Instant nextRetryAt = nextRetryAtCaptor.getValue();
    final Duration min = minBackoffDuration(2);
    final Duration max = maxBackoffDuration(2);
    assertThat(nextRetryAt).isAfterOrEqualTo(FIXED_NOW.plus(min));
    assertThat(nextRetryAt).isBeforeOrEqualTo(FIXED_NOW.plus(max));

    // DLQ 登録は発生しないことを確認する
    verifyNoInteractions(notificationDlqRepository);
  }

  @Test
  void handleFailureTreatsNullTransactionResultAsNotMoved() {
    // TransactionTemplate が null を返すケースでも NPE にならず処理を続行できることを確認する
    final NotificationRecord record = notificationRecord(PROPERTIES.maxAttempts() - 1);
    final RuntimeException failure = new IllegalStateException("boom");
    final PlatformTransactionManager transactionManager = new NoOpTransactionManager();
    final TransactionTemplate nullTemplate =
        new NullReturningTransactionTemplate(transactionManager);
    final NotificationDeliveryService nullReturningService =
        new NullReturningDeliveryService(
            notificationRepository,
            notificationDlqRepository,
            sender,
            PROPERTIES,
            metrics,
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            transactionManager,
            nullTemplate);

    nullReturningService.handleFailure(record, failure, FIXED_NOW, LOCKED_BY);

    verifyNoInteractions(notificationDlqRepository);
    verifyNoInteractions(notificationRepository);
  }

  @Test
  void resolveLockedByPrefersHostnameEnvOrFallbacks() {
    // 環境変数があれば優先し、無ければ空でない値へフォールバックする
    final String env = System.getenv("HOSTNAME");

    final String resolved = service.resolveLockedBy();

    if (env != null && !env.isBlank()) {
      assertThat(resolved).isEqualTo(env);
    } else {
      assertThat(resolved).isNotBlank();
    }
  }

  private NotificationRecord notificationRecord(int attemptCount) {
    // handleFailure に必要な最小情報を固定値で用意し、テストの揺れを避ける
    final Instant leaseUntil = FIXED_NOW.plus(PROPERTIES.lease());
    return new NotificationRecord(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "u_1",
        "EntitlementGranted",
        FIXED_NOW,
        "{}",
        NotificationStatus.PROCESSING,
        LOCKED_BY,
        FIXED_NOW,
        leaseUntil,
        attemptCount,
        null,
        FIXED_NOW,
        null);
  }

  private Duration minBackoffDuration(int attempt) {
    // ジッター下限を適用した期待レンジの最小値
    final double exp =
        PROPERTIES.backoffBase().toMillis()
            * Math.pow(PROPERTIES.backoffExponentBase(), (attempt - 1));
    final double capped = Math.min(exp, PROPERTIES.backoffMax().toMillis());
    final long backoffMillis = (long) Math.ceil(capped * PROPERTIES.backoffJitterMin());
    final long minMillis = PROPERTIES.backoffMin().toMillis();
    return Duration.ofMillis(Math.max(minMillis, backoffMillis));
  }

  private Duration maxBackoffDuration(int attempt) {
    // ジッター上限を適用した期待レンジの最大値
    final double exp =
        PROPERTIES.backoffBase().toMillis()
            * Math.pow(PROPERTIES.backoffExponentBase(), (attempt - 1));
    final double capped = Math.min(exp, PROPERTIES.backoffMax().toMillis());
    final long backoffMillis = (long) Math.ceil(capped * PROPERTIES.backoffJitterMax());
    final long minMillis = PROPERTIES.backoffMin().toMillis();
    return Duration.ofMillis(Math.max(minMillis, backoffMillis));
  }

  private static class NoOpTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {}

    @Override
    public void rollback(TransactionStatus status) {}
  }

  private static final class NullReturningTransactionTemplate extends TransactionTemplate {
    private static final long serialVersionUID = 1L;

    private NullReturningTransactionTemplate(PlatformTransactionManager transactionManager) {
      super(transactionManager);
    }

    @Override
    public <T> T execute(TransactionCallback<T> action) {
      return null;
    }
  }

  private static final class NullReturningDeliveryService extends NotificationDeliveryService {

    private final TransactionTemplate transactionTemplate;

    private NullReturningDeliveryService(
        NotificationRepository notificationRepository,
        NotificationDlqRepository notificationDlqRepository,
        NotificationSender sender,
        NotificationDeliveryProperties properties,
        NotificationMetrics metrics,
        Clock clock,
        PlatformTransactionManager transactionManager,
        TransactionTemplate transactionTemplate) {
      super(
          notificationRepository,
          notificationDlqRepository,
          sender,
          properties,
          metrics,
          clock,
          transactionManager);
      this.transactionTemplate = transactionTemplate;
    }

    @Override
    TransactionTemplate transactionTemplate() {
      return transactionTemplate;
    }
  }
}
