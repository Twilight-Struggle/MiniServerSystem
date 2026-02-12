/*
 * どこで: app/account/src/main/java/com/example/account/api/ApiErrorResponse.java
 * 何を: API エラー応答の共通 DTO
 * なぜ: エラー形式を統一し、BFF 側で機械的に処理できるようにするため
 */
package com.example.account.api;

public record ApiErrorResponse(
        String code,
        String message) {
}
