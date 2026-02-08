/*
 * どこで: Entitlement データアクセス
 * 何を: entitlements の登録/更新/参照を行う
 * なぜ: API とイベント生成で一貫した DB 操作を提供するため
 */
package com.example.entitlement.repository;

import static com.example.common.JdbcTimestampUtils.toTimestamp;

import com.example.entitlement.model.EntitlementRecord;
import com.example.entitlement.model.EntitlementStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EntitlementRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public Optional<EntitlementRecord> upsertGrantIfNotActive(
      String userId,
      String stockKeepingUnit,
      Instant grantedAt,
      String source,
      String sourceId,
      Instant updatedAt) {
    // 既に ACTIVE の場合は更新せず、空結果を返す
    final String sql =
        """
        INSERT INTO entitlements (
          user_id,
          stock_keeping_unit,
          status,
          granted_at,
          revoked_at,
          source,
          source_id,
          version,
          updated_at
        ) VALUES (
          :userId,
          :stockKeepingUnit,
          :status,
          :grantedAt,
          NULL,
          :source,
          :sourceId,
          0,
          :updatedAt
        )
        ON CONFLICT (user_id, stock_keeping_unit)
        DO UPDATE SET
          status = EXCLUDED.status,
          granted_at = EXCLUDED.granted_at,
          revoked_at = NULL,
          source = EXCLUDED.source,
          source_id = EXCLUDED.source_id,
          version = entitlements.version + 1,
          updated_at = EXCLUDED.updated_at
        WHERE entitlements.status <> 'ACTIVE'
        RETURNING user_id, stock_keeping_unit, status, version, updated_at;
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("stockKeepingUnit", stockKeepingUnit)
            .addValue("status", EntitlementStatus.ACTIVE.name())
            .addValue("grantedAt", toTimestamp(grantedAt))
            .addValue("source", source)
            .addValue("sourceId", sourceId)
            .addValue("updatedAt", toTimestamp(updatedAt));
    return jdbcTemplate.query(sql, params, this::mapRow).stream().findFirst();
  }

  public Optional<EntitlementRecord> upsertRevokeIfNotRevoked(
      String userId,
      String stockKeepingUnit,
      Instant revokedAt,
      String source,
      String sourceId,
      Instant updatedAt) {
    // 既に REVOKED の場合は更新せず、空結果を返す
    final String sql =
        """
        INSERT INTO entitlements (
          user_id,
          stock_keeping_unit,
          status,
          granted_at,
          revoked_at,
          source,
          source_id,
          version,
          updated_at
        ) VALUES (
          :userId,
          :stockKeepingUnit,
          :status,
          NULL,
          :revokedAt,
          :source,
          :sourceId,
          0,
          :updatedAt
        )
        ON CONFLICT (user_id, stock_keeping_unit)
        DO UPDATE SET
          status = EXCLUDED.status,
          granted_at = entitlements.granted_at,
          revoked_at = EXCLUDED.revoked_at,
          source = EXCLUDED.source,
          source_id = EXCLUDED.source_id,
          version = entitlements.version + 1,
          updated_at = EXCLUDED.updated_at
        WHERE entitlements.status <> 'REVOKED'
        RETURNING user_id, stock_keeping_unit, status, version, updated_at;
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("stockKeepingUnit", stockKeepingUnit)
            .addValue("status", EntitlementStatus.REVOKED.name())
            .addValue("revokedAt", toTimestamp(revokedAt))
            .addValue("source", source)
            .addValue("sourceId", sourceId)
            .addValue("updatedAt", toTimestamp(updatedAt));
    return jdbcTemplate.query(sql, params, this::mapRow).stream().findFirst();
  }

  public List<EntitlementRecord> findByUserId(String userId) {
    final String sql =
        """
        SELECT user_id, stock_keeping_unit, status, version, updated_at
        FROM entitlements
        WHERE user_id = :userId
        ORDER BY updated_at DESC
        """;
    final MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId);
    return jdbcTemplate.query(sql, params, this::mapRow);
  }

  private EntitlementRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new EntitlementRecord(
        rs.getString("user_id"),
        rs.getString("stock_keeping_unit"),
        EntitlementStatus.valueOf(rs.getString("status")),
        rs.getLong("version"),
        rs.getTimestamp("updated_at").toInstant());
  }
}
