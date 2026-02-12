/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/service/SessionService.java
 * 何を: 認証セッションの保存/取得/破棄を担当
 * なぜ: Cookie とサーバーセッションの責務を分離するため
 */
package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    /**
     * 役割:
     * - 認証済みユーザーをセッションへ保存し、Cookie 発行に必要な情報を返す。
     *
     * 期待動作:
     * - TTL は 1 時間を適用する。
     * - Cookie 属性は Secure=false, SameSite=Lax, Domain 完全一致で設定する。
     * - 実装時は local/ci/prod で Secure を切替可能な設計にする。
     */
    public String createSession(AuthenticatedUser user) {
        throw new UnsupportedOperationException("createSession is not implemented yet");
    }

    /**
     * 役割:
     * - セッション ID から認証済みユーザーを復元する。
     *
     * 期待動作:
     * - セッション未存在や期限切れ時は Optional.empty を返す。
     * - 復元データの account_status/roles を認可判定にそのまま利用できる形で返す。
     */
    public Optional<AuthenticatedUser> findAuthenticatedUser(String sessionId) {
        throw new UnsupportedOperationException("findAuthenticatedUser is not implemented yet");
    }

    /**
     * 役割:
     * - セッションを明示的に無効化する。
     *
     * 期待動作:
     * - ログアウト時にサーバー側セッションを削除し、再利用を防止する。
     * - 既に削除済みでも安全に完了する冪等操作にする。
     */
    public void deleteSession(String sessionId) {
        throw new UnsupportedOperationException("deleteSession is not implemented yet");
    }
}
