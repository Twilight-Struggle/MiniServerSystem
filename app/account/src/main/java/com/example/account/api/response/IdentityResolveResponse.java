/*
 * どこで: app/account/src/main/java/com/example/account/api/response/IdentityResolveResponse.java
 * 何を: POST /identities:resolve の出力 DTO
 * なぜ: BFF がセッション確立と認可判定に必要な最小属性を受け取るため
 */
package com.example.account.api.response;

import java.util.List;

public record IdentityResolveResponse(
        String userId,
        String accountStatus,
        List<String> roles) {
}
