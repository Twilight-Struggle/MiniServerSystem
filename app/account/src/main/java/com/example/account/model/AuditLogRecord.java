/*
 * どこで: app/account/src/main/java/com/example/account/model/AuditLogRecord.java
 * 何を: audit_logs テーブル相当のドメインレコード
 * なぜ: 管理操作の追跡可能性と監査性を担保するため
 */
package com.example.account.model;

import java.time.Instant;

public record AuditLogRecord(
        String id,
        String actorUserId,
        String action,
        String targetUserId,
        String metadataJson,
        Instant createdAt) {
}
