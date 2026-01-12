/*
 * どこで: IdempotencyKeyRepository の統合テスト
 * 何を: insert/find/upsertIfExpired の基本動作を検証する
 * なぜ: 冪等性の保存が DB 方言で壊れないことを保証するため
 */
package com.example.entitlement.repository;

import com.example.entitlement.AbstractPostgresContainerTest;
import com.example.entitlement.model.IdempotencyRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyKeyRepositoryTest extends AbstractPostgresContainerTest {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String IDEM_KEY = "idem-1";
    private static final String IDEM_KEY_ACTIVE = "idem-2";
    private static final String REQUEST_HASH = "hash-1";
    private static final String REQUEST_HASH_ALT = "hash-2";
    private static final int RESPONSE_CODE = 200;
    private static final int RESPONSE_CODE_ALT = 409;
    private static final String RESPONSE_BODY = "{\"ok\":true}";
    private static final String RESPONSE_BODY_ALT = "{\"ok\":false}";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM idempotency_keys", new MapSqlParameterSource());
    }

    @Test
    void insertAndFindReturnsRecord() {
        // DBのnow()判定で期限切れ扱いにならないよう、現在時刻基準でexpires_atを設定する。
        Instant now = Instant.now();
        IdempotencyRecord record = new IdempotencyRecord(
                IDEM_KEY,
                REQUEST_HASH,
                RESPONSE_CODE,
                RESPONSE_BODY,
                now.plus(TTL));

        idempotencyKeyRepository.insert(record);

        Optional<IdempotencyRecord> stored = idempotencyKeyRepository.findByKey(IDEM_KEY);

        assertThat(stored).isPresent();
        assertThat(stored.get().idempotencyKey()).isEqualTo(IDEM_KEY);
        assertThat(stored.get().requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(stored.get().responseCode()).isEqualTo(RESPONSE_CODE);
        assertJsonEqual(RESPONSE_BODY, stored.get().responseBodyJson());
        assertInstantCloseToMicros(now.plus(TTL), stored.get().expiresAt());
    }

    @Test
    void upsertIfExpiredInsertsWhenMissing() {
        // 未存在ならそのまま挿入できることを確認する。
        Instant now = Instant.now();
        IdempotencyRecord record = new IdempotencyRecord(
                IDEM_KEY,
                REQUEST_HASH,
                RESPONSE_CODE,
                RESPONSE_BODY,
                now.plus(TTL));

        int updated = idempotencyKeyRepository.upsertIfExpired(record);

        Optional<IdempotencyRecord> stored = idempotencyKeyRepository.findByKey(IDEM_KEY);

        assertThat(updated).isEqualTo(1);
        assertThat(stored).isPresent();
        assertThat(stored.get().requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(stored.get().responseCode()).isEqualTo(RESPONSE_CODE);
        assertJsonEqual(RESPONSE_BODY, stored.get().responseBodyJson());
    }

    @Test
    void upsertIfExpiredReturnsZeroWhenNotExpired() {
        // 未期限切れの既存行は更新せず、0件になることを確認する。
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TTL);
        IdempotencyRecord existing = new IdempotencyRecord(
                IDEM_KEY,
                REQUEST_HASH,
                RESPONSE_CODE,
                RESPONSE_BODY,
                expiresAt);
        idempotencyKeyRepository.insert(existing);

        IdempotencyRecord desired = new IdempotencyRecord(
                IDEM_KEY,
                REQUEST_HASH_ALT,
                RESPONSE_CODE_ALT,
                RESPONSE_BODY_ALT,
                expiresAt.plus(TTL));

        int updated = idempotencyKeyRepository.upsertIfExpired(desired);

        Optional<IdempotencyRecord> stored = idempotencyKeyRepository.findByKey(IDEM_KEY);

        assertThat(updated).isZero();
        assertThat(stored).isPresent();
        assertThat(stored.get().idempotencyKey()).isEqualTo(IDEM_KEY);
        assertThat(stored.get().requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(stored.get().responseCode()).isEqualTo(RESPONSE_CODE);
        assertJsonEqual(RESPONSE_BODY, stored.get().responseBodyJson());
        assertInstantCloseToMicros(expiresAt, stored.get().expiresAt());
    }

    @Test
    void upsertIfExpiredUpdatesWhenExpired() {
        // 期限切れの既存行は更新されることを確認する。
        Instant now = Instant.now();
        Instant expiredAt = now.minus(TTL);
        IdempotencyRecord expired = new IdempotencyRecord(
                IDEM_KEY,
                REQUEST_HASH,
                RESPONSE_CODE,
                RESPONSE_BODY,
                expiredAt);
        idempotencyKeyRepository.insert(expired);

        Instant newExpiresAt = now.plus(TTL);
        IdempotencyRecord desired = new IdempotencyRecord(
                IDEM_KEY,
                REQUEST_HASH_ALT,
                RESPONSE_CODE_ALT,
                RESPONSE_BODY_ALT,
                newExpiresAt);

        int updated = idempotencyKeyRepository.upsertIfExpired(desired);

        Optional<IdempotencyRecord> stored = idempotencyKeyRepository.findByKey(IDEM_KEY);

        assertThat(updated).isEqualTo(1);
        assertThat(stored).isPresent();
        assertThat(stored.get().idempotencyKey()).isEqualTo(IDEM_KEY);
        assertThat(stored.get().requestHash()).isEqualTo(REQUEST_HASH_ALT);
        assertThat(stored.get().responseCode()).isEqualTo(RESPONSE_CODE_ALT);
        assertJsonEqual(RESPONSE_BODY_ALT, stored.get().responseBodyJson());
        assertInstantCloseToMicros(newExpiresAt, stored.get().expiresAt());
    }

    @Test
    void deleteExpiredRemovesOnlyExpired() {
        Instant now = Instant.parse("2026-01-17T00:00:00Z");
        IdempotencyRecord expired = new IdempotencyRecord(
                IDEM_KEY,
                REQUEST_HASH,
                RESPONSE_CODE,
                RESPONSE_BODY,
                now.minusSeconds(1));
        IdempotencyRecord active = new IdempotencyRecord(
                IDEM_KEY_ACTIVE,
                REQUEST_HASH,
                RESPONSE_CODE,
                RESPONSE_BODY,
                now.plus(TTL));
        idempotencyKeyRepository.insert(expired);
        idempotencyKeyRepository.insert(active);

        int deleted = idempotencyKeyRepository.deleteExpired(now);

        MapSqlParameterSource expiredParams = new MapSqlParameterSource().addValue("idemKey", IDEM_KEY);
        Integer expiredCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_keys WHERE idem_key = :idemKey",
                expiredParams,
                Integer.class);
        MapSqlParameterSource activeParams = new MapSqlParameterSource().addValue("idemKey", IDEM_KEY_ACTIVE);
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_keys WHERE idem_key = :idemKey",
                activeParams,
                Integer.class);

        assertThat(deleted).isEqualTo(1);
        assertThat(expiredCount).isZero();
        assertThat(activeCount).isEqualTo(1);
    }

    private void assertJsonEqual(String expectedJson, String actualJson) {
        try {
            JsonNode expected = OBJECT_MAPPER.readTree(expectedJson);
            JsonNode actual = OBJECT_MAPPER.readTree(actualJson);
            assertThat(actual).isEqualTo(expected);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse json for assertion", ex);
        }
    }

    private void assertInstantCloseToMicros(Instant expected, Instant actual) {
        Duration delta = Duration.between(expected, actual).abs();
        assertThat(delta).isLessThanOrEqualTo(ChronoUnit.MICROS.getDuration());
    }
}
