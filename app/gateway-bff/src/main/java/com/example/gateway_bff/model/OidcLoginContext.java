/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/model/OidcLoginContext.java
 * 何を: /login 時点で生成する state/nonce 情報
 * なぜ: /callback で CSRF/replay 検証を安全に行うため
 */
package com.example.gateway_bff.model;

import java.time.Instant;

public record OidcLoginContext(
        String state,
        String nonce,
        Instant issuedAt,
        Instant expiresAt) {
}
