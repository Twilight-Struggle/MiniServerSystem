/*
 * どこで: Entitlement ドメインモデル
 * 何を: entitlement_audit の登録用データを表す
 * なぜ: 監査ログの構築を呼び出し側から隠蔽するため
 */
package com.example.entitlement.model;

import java.time.Instant;
import java.util.UUID;

public record EntitlementAuditRecord(
    UUID auditId,
    Instant occurredAt,
    String userId,
    String stockKeepingUnit,
    String action,
    String source,
    String sourceId,
    String requestId,
    String detailJson) {}
