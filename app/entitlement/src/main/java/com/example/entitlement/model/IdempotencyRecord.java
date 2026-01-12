/*
 * どこで: Entitlement ドメインモデル
 * 何を: idempotency_keys テーブルの読込結果を表す
 * なぜ: 冪等応答の再利用を安全に行うため
 */
package com.example.entitlement.model;

import java.time.Instant;

public record IdempotencyRecord(
        String idempotencyKey,
        String requestHash,
        int responseCode,
        String responseBodyJson,
        Instant expiresAt) {
}
