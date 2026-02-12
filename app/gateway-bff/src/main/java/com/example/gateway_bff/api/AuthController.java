/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/api/AuthController.java
 * 何を: ログイン開始/コールバック/ログアウト/自分情報 API を提供
 * なぜ: 認証フローの入口を BFF に集約するため
 */
package com.example.gateway_bff.api;

import com.example.gateway_bff.api.response.LoginRedirectResponse;
import com.example.gateway_bff.api.response.MeResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.OidcCallbackService;
import com.example.gateway_bff.service.OidcLoginService;
import com.example.gateway_bff.service.SessionService;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AuthController {

    private static final String SESSION_COOKIE_NAME = "MSS_SESSION";
    private final OidcLoginService oidcLoginService;
    private final OidcCallbackService oidcCallbackService;
    private final SessionService sessionService;

    public AuthController(
            OidcLoginService oidcLoginService,
            OidcCallbackService oidcCallbackService,
            SessionService sessionService) {
        this.oidcLoginService = oidcLoginService;
        this.oidcCallbackService = oidcCallbackService;
        this.sessionService = sessionService;
    }

    /**
     * 役割:
     * - OIDC ログインを開始する。
     *
     * 期待動作:
     * - state/nonce を生成し、authorize URL へ遷移可能な情報を返す。
     * - 実装時は 302 リダイレクトまたは JSON 応答のどちらかを API 契約として固定する。
     */
    @GetMapping("/login")
    public ResponseEntity<LoginRedirectResponse> login() {
        return ResponseEntity.ok(oidcLoginService.prepareLogin());
    }

    /**
     * 役割:
     * - IdP からの callback を処理してログインを完了する。
     *
     * 期待動作:
     * - state/code を検証し、問題なければセッションを発行する。
     * - account_status が suspended のユーザーはログイン失敗にする。
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam("state") String state,
            @RequestParam("code") String code) {
        AuthenticatedUser user = oidcCallbackService.handleCallback(state, code);
        sessionService.createSession(user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 役割:
     * - 現在のセッションを破棄する。
     *
     * 期待動作:
     * - Cookie のセッション ID を元にサーバー側セッションを削除する。
     * - セッションが存在しない場合でも 204 を返す。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionService.deleteSession(sessionId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 役割:
     * - 現在ログイン中ユーザーの最小情報を返す。
     *
     * 期待動作:
     * - セッションが有効なら userId/status/roles を返す。
     * - セッション無効時は 401 を返す。
     */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(
            @CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        Optional<AuthenticatedUser> user = sessionService.findAuthenticatedUser(sessionId);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        MeResponse response = new MeResponse(user.get().userId(), user.get().accountStatus(), user.get().roles());
        return ResponseEntity.ok(response);
    }
}
