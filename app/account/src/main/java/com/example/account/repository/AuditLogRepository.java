package com.example.account.repository;

import com.example.account.model.AuditLogRecord;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class AuditLogRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public void insert(AuditLogRecord auditLogRecord) {
    final String sql =
        """
        INSERT INTO audit_logs (id, actor_user_id, action, target_user_id, metadata_json, created_at)
        VALUES (:id, :actorUserId, :action, :targetUserId, CAST(:metadataJson AS jsonb), :createdAt)
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", auditLogRecord.id())
            .addValue("actorUserId", auditLogRecord.actorUserId())
            .addValue("action", auditLogRecord.action())
            .addValue("targetUserId", auditLogRecord.targetUserId())
            .addValue("metadataJson", auditLogRecord.metadataJson())
            .addValue("createdAt", Timestamp.from(auditLogRecord.createdAt()));
    jdbcTemplate.update(sql, params);
  }
}
