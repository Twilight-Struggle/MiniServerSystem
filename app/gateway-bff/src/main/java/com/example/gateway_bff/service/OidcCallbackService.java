/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/service/OidcCallbackService.java
 * 何を: /callback の一連処理(code交換/検証/account解決/セッション発行)を担当
 * なぜ: 認証の主経路を単一のサービスでテスト可能にするため
 */
package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import org.springframework.stereotype.Service;

@Service
public class OidcCallbackService {

    /**
     * 役割:
     * - 受け取った state と code を検証し、ログインを完了させる。
     *
     * 期待動作:
     * - state 不一致は即時拒否する。
     * - token endpoint から id_token を取得し、署名/iss/aud/exp/nonce を検証する。
     * - claims を Account Service へ送信して userId/roles/status を解決する。
     * - account_status が active の場合のみセッションを発行し、認証結果を返す。
     */
    public AuthenticatedUser handleCallback(String state, String code) {
        throw new UnsupportedOperationException("handleCallback is not implemented yet");
    }
}
