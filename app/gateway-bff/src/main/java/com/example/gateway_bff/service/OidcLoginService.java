/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/service/OidcLoginService.java
 * 何を: /login 開始処理(state/nonce 生成 + authorize URL 構築)を担当
 * なぜ: コントローラーから OIDC 開始ロジックを分離するため
 */
package com.example.gateway_bff.service;

import com.example.gateway_bff.api.response.LoginRedirectResponse;
import org.springframework.stereotype.Service;

@Service
public class OidcLoginService {

    /**
     * 役割:
     * - state/nonce を生成し、サーバーサイドセッションへ保存する。
     * - Google OIDC の authorize URL を組み立てる。
     *
     * 期待動作:
     * - response_type=code, scope=openid profile email を必須で含める。
     * - state/nonce は十分なランダム性を持たせ、短時間で失効する。
     * - 返却値には authorizationUrl と state を含め、テストで検証可能にする。
     */
    public LoginRedirectResponse prepareLogin() {
        throw new UnsupportedOperationException("prepareLogin is not implemented yet");
    }
}
