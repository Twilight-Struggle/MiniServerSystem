/*
 * どこで: Entitlement ドメインモデル
 * 何を: entitlements テーブルのスナップショットを表す
 * なぜ: API 応答やイベント生成で共通化するため
 */
package com.example.entitlement.model;

import java.time.Instant;

public record EntitlementRecord(
        String userId,
        String stockKeepingUnit,
        EntitlementStatus status,
        long version,
        Instant updatedAt) {
}
