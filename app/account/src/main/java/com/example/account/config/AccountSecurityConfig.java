/*
 * どこで: app/account/src/main/java/com/example/account/config/AccountSecurityConfig.java
 * 何を: Account API の認可ポリシーを定義する設定クラス
 * なぜ: 管理 API のアクセス制御をアプリ内で最小限担保するため
 */
package com.example.account.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountSecurityConfig {

    /**
     * 役割:
     * - 管理 API を admin ロールに限定するセキュリティ設定を構築する。
     *
     * 期待動作:
     * - 実装時は Spring Security の SecurityFilterChain を返すメソッドへ差し替える。
     * - `/admin/**` は認証必須かつ admin 権限必須、`/identities:resolve` は BFF 間通信に限定する。
     * - ローカル開発時の簡易モードを設ける場合も、切替条件をプロファイルで明示する。
     */
    public void configureAuthorizationPolicy() {
        throw new UnsupportedOperationException("configureAuthorizationPolicy is not implemented yet");
    }
}
