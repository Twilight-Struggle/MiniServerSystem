/*
 * どこで: Entitlement API
 * 何を: エラー応答のコードを定義する
 * なぜ: 同じ HTTP ステータスでも原因を区別できるようにするため
 */
package com.example.entitlement.api;

public enum ApiErrorCode {
    BAD_REQUEST,
    IDEMPOTENCY_KEY_CONFLICT,
    ENTITLEMENT_STATE_CONFLICT
}
