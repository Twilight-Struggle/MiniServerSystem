/*
 * どこで: EntitlementRepository の統合テスト
 * 何を: 条件付き upsert の更新有無を検証する
 * なぜ: 状態衝突時に更新されないことを保証するため
 */
package com.example.entitlement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.entitlement.AbstractPostgresContainerTest;
import com.example.entitlement.model.EntitlementRecord;
import com.example.entitlement.model.EntitlementStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EntitlementRepositoryTest extends AbstractPostgresContainerTest {

  private static final Instant BASE_TIME = Instant.parse("2026-01-17T00:00:00Z");
  private static final String USER_ID = "user-1";
  private static final String SKU = "sku-1";
  private static final String SOURCE = "purchase";
  private static final String SOURCE_ID = "purchase-1";

  @Autowired private EntitlementRepository entitlementRepository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanup() {
    jdbcTemplate.update("DELETE FROM entitlements", new MapSqlParameterSource());
  }

  @Test
  void upsertGrantIfNotActiveReturnsEmptyWhenAlreadyActive() {
    final Optional<EntitlementRecord> first =
        entitlementRepository.upsertGrantIfNotActive(
            USER_ID, SKU, BASE_TIME, SOURCE, SOURCE_ID, BASE_TIME);

    final Optional<EntitlementRecord> second =
        entitlementRepository.upsertGrantIfNotActive(
            USER_ID, SKU, BASE_TIME.plusSeconds(60), SOURCE, SOURCE_ID, BASE_TIME.plusSeconds(60));

    assertThat(first).isPresent();
    assertThat(first.get().status()).isEqualTo(EntitlementStatus.ACTIVE);
    assertThat(second).isEmpty();

    final List<EntitlementRecord> records = entitlementRepository.findByUserId(USER_ID);
    assertThat(records).hasSize(1);
    assertThat(records.get(0).version()).isEqualTo(0);
    assertThat(records.get(0).updatedAt()).isEqualTo(BASE_TIME);
  }

  @Test
  void upsertRevokeIfNotRevokedReturnsEmptyWhenAlreadyRevoked() {
    final Optional<EntitlementRecord> first =
        entitlementRepository.upsertRevokeIfNotRevoked(
            USER_ID, SKU, BASE_TIME, SOURCE, SOURCE_ID, BASE_TIME);

    final Optional<EntitlementRecord> second =
        entitlementRepository.upsertRevokeIfNotRevoked(
            USER_ID, SKU, BASE_TIME.plusSeconds(60), SOURCE, SOURCE_ID, BASE_TIME.plusSeconds(60));

    assertThat(first).isPresent();
    assertThat(first.get().status()).isEqualTo(EntitlementStatus.REVOKED);
    assertThat(second).isEmpty();

    final List<EntitlementRecord> records = entitlementRepository.findByUserId(USER_ID);
    assertThat(records).hasSize(1);
    assertThat(records.get(0).version()).isEqualTo(0);
    assertThat(records.get(0).updatedAt()).isEqualTo(BASE_TIME);
  }
}
