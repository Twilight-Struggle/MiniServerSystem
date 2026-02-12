/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/config/GatewaySecurityConfig.java
 * 何を: BFF の認可ルールを定義する設定クラス
 * なぜ: 未ログイン拒否と admin 制御を一か所で管理するため
 */
package com.example.gateway_bff.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewaySecurityConfig {

    /**
     * 役割:
     * - ルートごとの認可ポリシーを定義する。
     *
     * 期待動作:
     * - `/login` と `/callback` は匿名アクセスを許可する。
     * - `/admin/**` は ADMIN ロール必須にする。
     * - 通常 API はログイン済みかつ account_status=active を必須とする。
     */
    public void configureAuthorizationPolicy() {
        throw new UnsupportedOperationException("configureAuthorizationPolicy is not implemented yet");
    }
}
