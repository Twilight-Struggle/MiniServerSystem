/*
 * どこで: app/account/src/main/java/com/example/account/model/UserRecord.java
 * 何を: users テーブル相当のドメインレコード
 * なぜ: API/Service/Repository 間でユーザー情報の受け渡しを明確にするため
 */
package com.example.account.model;

import java.time.Instant;

public record UserRecord(
        String userId,
        String displayName,
        String locale,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
