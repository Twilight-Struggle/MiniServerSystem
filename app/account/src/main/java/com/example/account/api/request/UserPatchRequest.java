/*
 * どこで: app/account/src/main/java/com/example/account/api/request/UserPatchRequest.java
 * 何を: PATCH /users/{userId} の入力 DTO
 * なぜ: 更新可能なプロフィール項目を限定し、将来の互換性を守るため
 */
package com.example.account.api.request;

public record UserPatchRequest(
        String displayName,
        String locale) {
}
