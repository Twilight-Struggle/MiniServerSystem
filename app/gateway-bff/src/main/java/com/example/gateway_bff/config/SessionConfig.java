/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/config/SessionConfig.java
 * 何を: セッションと Cookie の設定値を管理する設定クラス
 * なぜ: TTL/Cookie 属性を環境ごとに切替可能にするため
 */
package com.example.gateway_bff.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionConfig {

    /**
     * 役割:
     * - セッション有効期限を 1 時間に設定する。
     *
     * 期待動作:
     * - 期限切れ後は自動的に無効化される。
     * - 実装時は Spring Session(例: Redis)へ TTL が反映される形にする。
     */
    public void configureSessionTtl() {
        throw new UnsupportedOperationException("configureSessionTtl is not implemented yet");
    }

    /**
     * 役割:
     * - Cookie 属性(Secure/SameSite/Domain)を設定する。
     *
     * 期待動作:
     * - local 環境は Secure=false を既定値にする。
     * - SameSite=Lax, Domain 完全一致を適用する。
     * - 将来の ci/prod 向けに Secure=true へ切替可能にする。
     */
    public void configureCookiePolicy() {
        throw new UnsupportedOperationException("configureCookiePolicy is not implemented yet");
    }
}
