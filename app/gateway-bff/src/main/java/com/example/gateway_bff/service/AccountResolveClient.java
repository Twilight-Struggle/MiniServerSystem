/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/service/AccountResolveClient.java
 * 何を: Account Service の identities:resolve 呼び出しを担当
 * なぜ: 下流通信を専用クライアントへ分離し、再試行/タイムアウトを管理するため
 */
package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import org.springframework.stereotype.Service;

@Service
public class AccountResolveClient {

    /**
     * 役割:
     * - OIDC claims を Account API へ送信し、内部ユーザー情報へ変換する。
     *
     * 期待動作:
     * - provider/subject を必須で送信する。
     * - タイムアウトや下流障害を BFF 側で判定可能な例外へ変換する。
     * - account_status と roles をそのまま保持して呼び出し元へ返す。
     */
    public AuthenticatedUser resolveIdentity(OidcClaims claims) {
        throw new UnsupportedOperationException("resolveIdentity is not implemented yet");
    }
}
