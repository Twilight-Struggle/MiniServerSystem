package com.example.gateway_bff.api;

import com.example.gateway_bff.api.response.LoginRedirectResponse;
import com.example.gateway_bff.api.response.MeResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.OidcCallbackService;
import com.example.gateway_bff.service.OidcLoginService;
import com.example.gateway_bff.service.SessionService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@SuppressWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class AuthController {

  private static final String SESSION_COOKIE_NAME = "MSS_SESSION";
  private final OidcLoginService oidcLoginService;
  private final OidcCallbackService oidcCallbackService;
  private final SessionService sessionService;

  @GetMapping("/login")
  public ResponseEntity<LoginRedirectResponse> login() {
    return ResponseEntity.ok(oidcLoginService.prepareLogin());
  }

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam("state") String state, @RequestParam("code") String code) {
    final AuthenticatedUser user = oidcCallbackService.handleCallback(state, code);
    sessionService.createSession(user);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionId) {
    if (sessionId != null && !sessionId.isBlank()) {
      sessionService.deleteSession(sessionId);
    }
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(
      @CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return ResponseEntity.status(401).build();
    }
    final Optional<AuthenticatedUser> user = sessionService.findAuthenticatedUser(sessionId);
    if (user.isEmpty()) {
      return ResponseEntity.status(401).build();
    }
    final MeResponse response =
        new MeResponse(user.get().userId(), user.get().accountStatus(), user.get().roles());
    return ResponseEntity.ok(response);
  }
}
