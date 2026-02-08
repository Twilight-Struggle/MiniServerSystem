/*
 * どこで: Entitlement データアクセス
 * 何を: idempotency_keys の登録/取得を担う
 * なぜ: API 再送時に同一レスポンスを返すため
 */
package com.example.entitlement.repository;

import static com.example.common.JdbcTimestampUtils.toTimestamp;

import com.example.entitlement.model.IdempotencyRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class IdempotencyKeyRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockByKey(long lockKey) {
    // 同一Idempotency-Keyをトランザクション内で直列化する。
    // 64-bit の advisory lock を使い、hashtext(32-bit) の衝突を避ける。
    final String sql = "SELECT pg_advisory_xact_lock(:lockKey)";
    final MapSqlParameterSource params = new MapSqlParameterSource().addValue("lockKey", lockKey);
    jdbcTemplate.query(sql, params, rs -> null);
  }

  public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
    final String sql =
        """
        SELECT idem_key, request_hash, response_code, response_body::text AS response_body_text, expires_at
        FROM idempotency_keys
        WHERE idem_key = :idempotencyKey
          AND expires_at > now()
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey);
    return jdbcTemplate.query(sql, params, this::mapRow).stream().findFirst();
  }

  public int insert(IdempotencyRecord record) {
    final String sql =
        """
        INSERT INTO idempotency_keys (
          idem_key,
          request_hash,
          response_code,
          response_body,
          expires_at
        ) VALUES (
          :idempotencyKey,
          :requestHash,
          :responseCode,
          :responseBody::jsonb,
          :expiresAt
        )
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("idempotencyKey", record.idempotencyKey())
            .addValue("requestHash", record.requestHash())
            .addValue("responseCode", record.responseCode())
            .addValue("responseBody", record.responseBodyJson())
            .addValue("expiresAt", toTimestamp(record.expiresAt()));
    return jdbcTemplate.update(sql, params);
  }

  public int upsertIfExpired(IdempotencyRecord record) {
    final String sql =
        """
        INSERT INTO idempotency_keys (
          idem_key,
          request_hash,
          response_code,
          response_body,
          expires_at
        ) VALUES (
          :idempotencyKey,
          :requestHash,
          :responseCode,
          :responseBody::jsonb,
          :expiresAt
        )
        ON CONFLICT (idem_key) DO UPDATE
          SET
            request_hash  = EXCLUDED.request_hash,
            response_code = EXCLUDED.response_code,
            response_body = EXCLUDED.response_body,
            expires_at    = EXCLUDED.expires_at
        WHERE idempotency_keys.expires_at <= now()
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("idempotencyKey", record.idempotencyKey())
            .addValue("requestHash", record.requestHash())
            .addValue("responseCode", record.responseCode())
            .addValue("responseBody", record.responseBodyJson())
            .addValue("expiresAt", toTimestamp(record.expiresAt()));
    // 1=保存成功、0=未期限切れが存在して更新されなかった(不変条件違反)。
    return jdbcTemplate.update(sql, params);
  }

  public int deleteExpired(Instant now) {
    // 冪等性の TTL は expires_at に保持されているため、期限切れのみ削除する。
    final String sql =
        """
        DELETE FROM idempotency_keys
        WHERE expires_at <= :now
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("now", toTimestamp(now));
    return jdbcTemplate.update(sql, params);
  }

  private IdempotencyRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new IdempotencyRecord(
        rs.getString("idem_key"),
        rs.getString("request_hash"),
        rs.getInt("response_code"),
        rs.getString("response_body_text"),
        rs.getTimestamp("expires_at").toInstant());
  }
}
