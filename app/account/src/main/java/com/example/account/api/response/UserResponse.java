/*
 * どこで: app/account/src/main/java/com/example/account/api/response/UserResponse.java
 * 何を: GET/PATCH /users/{userId} の出力 DTO
 * なぜ: クライアントに返すユーザー属性を安定した契約として扱うため
 */
package com.example.account.api.response;

import java.util.List;

public record UserResponse(
        String userId,
        String displayName,
        String locale,
        String status,
        List<String> roles) {
}
