/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/api/response/LoginRedirectResponse.java
 * 何を: /login の戻り値に利用する DTO
 * なぜ: テストやデバッグ時にリダイレクト先を明示的に扱えるようにするため
 */
package com.example.gateway_bff.api.response;

public record LoginRedirectResponse(
        String authorizationUrl,
        String state) {
}
