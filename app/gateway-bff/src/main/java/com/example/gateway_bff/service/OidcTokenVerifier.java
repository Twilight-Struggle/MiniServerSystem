/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/service/OidcTokenVerifier.java
 * 何を: id_token の検証と claims 抽出を担当
 * なぜ: セキュリティ上重要な検証を独立させて厳密にテストするため
 */
package com.example.gateway_bff.service;

import com.example.gateway_bff.model.OidcClaims;
import org.springframework.stereotype.Service;

@Service
public class OidcTokenVerifier {

    /**
     * 役割:
     * - id_token を検証し、業務で使う claims を抽出する。
     *
     * 期待動作:
     * - JWKS を利用して署名を検証する。
     * - iss/aud/exp/nonce を必須検証し、いずれか不一致なら例外を返す。
     * - clock skew 許容値と JWKS キャッシュ戦略を設定値で調整可能にする。
     */
    public OidcClaims verify(String idToken, String expectedNonce) {
        throw new UnsupportedOperationException("verify is not implemented yet");
    }
}
