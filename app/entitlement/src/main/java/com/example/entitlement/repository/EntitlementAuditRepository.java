/*
 * どこで: Entitlement データアクセス
 * 何を: entitlement_audit の登録を行う
 * なぜ: 操作履歴を追跡できるようにするため
 */
package com.example.entitlement.repository;

import com.example.entitlement.model.EntitlementAuditRecord;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import static com.example.common.JdbcTimestampUtils.toTimestamp;

@Repository
@RequiredArgsConstructor
public class EntitlementAuditRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public int insert(EntitlementAuditRecord record) {
    String sql = """
        INSERT INTO entitlement_audit (
          audit_id,
          occurred_at,
          user_id,
          stock_keeping_unit,
          action,
          source,
          source_id,
          request_id,
          detail
        ) VALUES (
          :auditId,
          :occurredAt,
          :userId,
          :stockKeepingUnit,
          :action,
          :source,
          :sourceId,
          :requestId,
          :detail::jsonb
        )
        """;
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("auditId", record.auditId())
        .addValue("occurredAt", toTimestamp(record.occurredAt()))
        .addValue("userId", record.userId())
        .addValue("stockKeepingUnit", record.stockKeepingUnit())
        .addValue("action", record.action())
        .addValue("source", record.source())
        .addValue("sourceId", record.sourceId())
        .addValue("requestId", record.requestId())
        .addValue("detail", record.detailJson());
    return jdbcTemplate.update(sql, params);
  }

}
