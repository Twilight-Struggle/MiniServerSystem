/*
 * どこで: EntitlementService の統合テスト
 * 何を: 状態衝突時の冪等エラー保存を検証する
 * なぜ: 失敗応答も再利用できることを保証するため
 */
package com.example.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.entitlement.AbstractPostgresContainerTest;
import com.example.entitlement.api.ApiErrorCode;
import com.example.entitlement.api.ApiErrorResponse;
import com.example.entitlement.api.EntitlementRequest;
import com.example.entitlement.api.EntitlementResponse;
import com.example.entitlement.api.IdempotencyConflictException;
import com.example.entitlement.api.InvalidEntitlementTransitionException;
import com.example.entitlement.model.EntitlementStatus;
import com.example.entitlement.model.IdempotencyRecord;
import com.example.entitlement.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EntitlementServiceTest extends AbstractPostgresContainerTest {

  private static final String USER_ID = "user-1";
  private static final String SKU = "sku-1";
  private static final String REASON = "purchase";
  private static final String PURCHASE_ID = "purchase-1";
  private static final int CONCURRENT_THREADS = 2;
  // 同時実行テストがハングしないよう、待機時間を短めに固定する。
  private static final Duration LATCH_TIMEOUT = Duration.ofSeconds(5);
  // 直列化/再利用の完了を待つため、開始合図より少し長めに設定する。
  private static final Duration COMPLETION_TIMEOUT = Duration.ofSeconds(10);

  @Autowired private EntitlementService entitlementService;

  @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private RequestHasher requestHasher;

  @BeforeEach
  void cleanup() {
    final MapSqlParameterSource params = new MapSqlParameterSource();
    jdbcTemplate.update("DELETE FROM entitlement_audit", params);
    jdbcTemplate.update("DELETE FROM outbox_events", params);
    jdbcTemplate.update("DELETE FROM entitlements", params);
    jdbcTemplate.update("DELETE FROM idempotency_keys", params);
  }

  @Test
  void grantStoresIdempotencyErrorWhenAlreadyActive() throws JsonProcessingException {
    final EntitlementRequest request = new EntitlementRequest(USER_ID, SKU, REASON, PURCHASE_ID);

    final EntitlementResponse success =
        entitlementService.grant(request, "idem-success", "trace-1");

    assertThat(success.status()).isEqualTo(EntitlementStatus.ACTIVE.name());

    assertThatThrownBy(() -> entitlementService.grant(request, "idem-error", "trace-2"))
        .isInstanceOf(InvalidEntitlementTransitionException.class)
        .hasMessage("already ACTIVE");

    final Optional<IdempotencyRecord> stored = idempotencyKeyRepository.findByKey("idem-error");

    assertThat(stored).isPresent();
    assertThat(stored.get().responseCode()).isEqualTo(HttpStatus.CONFLICT.value());

    final ApiErrorResponse errorResponse =
        objectMapper.readValue(stored.get().responseBodyJson(), ApiErrorResponse.class);

    assertThat(errorResponse.code()).isEqualTo(ApiErrorCode.ENTITLEMENT_STATE_CONFLICT);
    assertThat(errorResponse.message()).isEqualTo("already ACTIVE");

    assertThatThrownBy(() -> entitlementService.grant(request, "idem-error", "trace-2"))
        .isInstanceOf(InvalidEntitlementTransitionException.class)
        .hasMessage("already ACTIVE");
  }

  @Test
  void grantReturnsIdempotencyConflictWhenSameKeyDifferentRequest() {
    final EntitlementRequest request = new EntitlementRequest(USER_ID, SKU, REASON, PURCHASE_ID);
    final EntitlementRequest otherRequest =
        new EntitlementRequest(USER_ID, SKU, REASON, PURCHASE_ID + "-2");

    final EntitlementResponse success =
        entitlementService.grant(request, "idem-conflict", "trace-1");

    assertThat(success.status()).isEqualTo(EntitlementStatus.ACTIVE.name());

    assertThatThrownBy(() -> entitlementService.grant(otherRequest, "idem-conflict", "trace-2"))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("Idempotency-Key conflict");
  }

  @Test
  void grantReusesStoredIdempotencyConflictResponse() throws JsonProcessingException {
    final EntitlementRequest request = new EntitlementRequest(USER_ID, SKU, REASON, PURCHASE_ID);
    final String idempotencyKey = "idem-stored-conflict";
    final String requestHash = requestHasher.hash("GRANT", request);
    final ApiErrorResponse errorResponse =
        new ApiErrorResponse(ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT, "Idempotency-Key conflict");
    final String responseJson = objectMapper.writeValueAsString(errorResponse);
    // テスト中に期限切れにならないよう、十分先の expires_at を設定する。
    final Instant expiresAt = Instant.now().plus(Duration.ofHours(1));
    idempotencyKeyRepository.insert(
        new IdempotencyRecord(
            idempotencyKey, requestHash, HttpStatus.CONFLICT.value(), responseJson, expiresAt));

    assertThatThrownBy(() -> entitlementService.grant(request, idempotencyKey, "trace-1"))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("Idempotency-Key conflict");
  }

  @Test
  void grantReturnsSameResponseWhenIdempotencyHit() {
    final EntitlementRequest request = new EntitlementRequest(USER_ID, SKU, REASON, PURCHASE_ID);

    final EntitlementResponse first = entitlementService.grant(request, "idem-reuse", "trace-1");
    final EntitlementResponse second = entitlementService.grant(request, "idem-reuse", "trace-2");

    assertThat(second).isEqualTo(first);
  }

  @Test
  void grantSerializesSameIdempotencyKeyWhenConcurrent()
      throws InterruptedException, ExecutionException, TimeoutException {
    final EntitlementRequest request = new EntitlementRequest(USER_ID, SKU, REASON, PURCHASE_ID);
    // 同時開始でロック競合を再現し、直列化 or reuse の挙動を確認する。
    final CountDownLatch ready = new CountDownLatch(CONCURRENT_THREADS);
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(CONCURRENT_THREADS);
    // スレッド安全なコレクションで結果と例外を回収する。
    final List<EntitlementResponse> responses = new CopyOnWriteArrayList<>();
    final List<Throwable> errors = new CopyOnWriteArrayList<>();
    final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
    final List<Future<?>> futures = new ArrayList<>(CONCURRENT_THREADS);

    try {
      final Runnable task =
          () -> {
            try {
              // 2スレッドが揃ってから開始し、直列化が必要な状況を作る。
              ready.countDown();
              if (!start.await(LATCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                errors.add(new IllegalStateException("start latch timeout"));
                return;
              }
              final EntitlementResponse response =
                  entitlementService.grant(
                      request, "idem-concurrent", "trace-" + Thread.currentThread().threadId());
              responses.add(response);
            } catch (Throwable ex) {
              errors.add(ex);
            } finally {
              done.countDown();
            }
          };

      futures.add(executor.submit(task));
      futures.add(executor.submit(task));

      // 両タスクの準備完了を待ってから同時に開始する。
      assertThat(ready.await(LATCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
      start.countDown();
      // 完了待ちはデッドロック検知の安全弁として設ける。
      assertThat(done.await(COMPLETION_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
      // submit() の戻り値は無視せず、完了確認で SpotBugs を回避する。
      for (Future<?> future : futures) {
        future.get(COMPLETION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      }
    } finally {
      // テスト失敗時もスレッドを解放し、後続テストに影響させない。
      executor.shutdownNow();
    }

    // 例外なく2件の応答が返り、同一レスポンスであることを確認する。
    assertThat(errors).isEmpty();
    assertThat(responses).hasSize(CONCURRENT_THREADS);
    assertThat(responses.get(0)).isEqualTo(responses.get(1));
    // 同一Idempotency-Keyの同時実行でも副作用が1回のみであることを確認する。
    assertThat(countTable("entitlements")).isEqualTo(1);
    assertThat(countTable("outbox_events")).isEqualTo(1);
    assertThat(countTable("entitlement_audit")).isEqualTo(1);
    assertThat(countTable("idempotency_keys")).isEqualTo(1);
  }

  @Test
  void grantDoesNotDuplicateSideEffectsWhenIdempotencyReplayed() {
    final EntitlementRequest request = new EntitlementRequest(USER_ID, SKU, REASON, PURCHASE_ID);

    final EntitlementResponse first = entitlementService.grant(request, "idem-replay", "trace-1");

    final int entitlementsAfterFirst = countTable("entitlements");
    final int outboxAfterFirst = countTable("outbox_events");
    final int auditAfterFirst = countTable("entitlement_audit");
    final int idempotencyAfterFirst = countTable("idempotency_keys");

    // 1回目の成功で各テーブルが1件ずつ増えることを確認する
    assertThat(entitlementsAfterFirst).isEqualTo(1);
    assertThat(outboxAfterFirst).isEqualTo(1);
    assertThat(auditAfterFirst).isEqualTo(1);
    assertThat(idempotencyAfterFirst).isEqualTo(1);

    final EntitlementResponse second = entitlementService.grant(request, "idem-replay", "trace-2");

    // 再送時は同一レスポンスかつ副作用が増えない
    assertThat(second).isEqualTo(first);
    assertThat(countTable("entitlements")).isEqualTo(entitlementsAfterFirst);
    assertThat(countTable("outbox_events")).isEqualTo(outboxAfterFirst);
    assertThat(countTable("entitlement_audit")).isEqualTo(auditAfterFirst);
    assertThat(countTable("idempotency_keys")).isEqualTo(idempotencyAfterFirst);
  }

  private int countTable(String table) {
    final String sql =
        switch (table) {
          case "entitlements" -> "SELECT count(*) FROM entitlements";
          case "outbox_events" -> "SELECT count(*) FROM outbox_events";
          case "entitlement_audit" -> "SELECT count(*) FROM entitlement_audit";
          case "idempotency_keys" -> "SELECT count(*) FROM idempotency_keys";
          default -> throw new IllegalArgumentException("unsupported table: " + table);
        };
    final Integer count =
        jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
    return count == null ? 0 : count;
  }
}
