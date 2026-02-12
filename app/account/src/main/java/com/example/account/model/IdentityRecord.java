/*
 * どこで: app/account/src/main/java/com/example/account/model/IdentityRecord.java
 * 何を: identities テーブル相当のドメインレコード
 * なぜ: provider + subject による同定情報を正規化して扱うため
 */
package com.example.account.model;

import java.time.Instant;

public record IdentityRecord(
        String provider,
        String subject,
        String userId,
        String email,
        boolean emailVerified,
        Instant createdAt) {
}
