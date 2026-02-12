/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/api/response/MeResponse.java
 * 何を: /me の出力 DTO
 * なぜ: セッションから復元した認証情報をクライアントへ返すため
 */
package com.example.gateway_bff.api.response;

import java.util.List;

public record MeResponse(
        String userId,
        String accountStatus,
        List<String> roles) {
}
