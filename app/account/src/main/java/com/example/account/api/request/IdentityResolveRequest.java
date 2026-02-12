/*
 * どこで: app/account/src/main/java/com/example/account/api/request/IdentityResolveRequest.java
 * 何を: POST /identities:resolve の入力 DTO
 * なぜ: BFF から受け取る OIDC claims を API 境界で明示するため
 */
package com.example.account.api.request;

public record IdentityResolveRequest(
        String provider,
        String subject,
        String email,
        Boolean emailVerified,
        String name,
        String picture) {
}
