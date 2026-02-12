/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/model/OidcClaims.java
 * 何を: id_token から抽出した主要 claims を保持するモデル
 * なぜ: トークン処理と業務処理を分離してテストしやすくするため
 */
package com.example.gateway_bff.model;

public record OidcClaims(
        String provider,
        String subject,
        String email,
        boolean emailVerified,
        String name,
        String picture,
        String issuer,
        String audience,
        long expiresAtEpochSeconds,
        String nonce) {
}
