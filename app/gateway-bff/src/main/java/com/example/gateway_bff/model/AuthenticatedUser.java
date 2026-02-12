/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/model/AuthenticatedUser.java
 * 何を: セッションへ保存する認証済みユーザー情報
 * なぜ: 後続 API の認可判定に必要な最小情報を安定して保持するため
 */
package com.example.gateway_bff.model;

import java.util.List;

public record AuthenticatedUser(
        String userId,
        String accountStatus,
        List<String> roles) {
}
