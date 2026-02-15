package com.example.gateway_bff.api;

import com.example.gateway_bff.api.response.MeResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.OidcAuthenticatedUserService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

  private static final URI OIDC_AUTHORIZATION_URI = URI.create("/oauth2/authorization/keycloak");
  private final OidcAuthenticatedUserService oidcAuthenticatedUserService;

  @GetMapping("/login")
  public ResponseEntity<Void> login(
      @RequestParam(name = "error", required = false) String error) {
    if (error != null) {
      return ResponseEntity.status(401).build();
    }
    return ResponseEntity.status(302).location(OIDC_AUTHORIZATION_URI).build();
  }

  @GetMapping("/v1/me")
  public ResponseEntity<MeResponse> me(Authentication authentication) {
    final AuthenticatedUser user =
        oidcAuthenticatedUserService.resolveAuthenticatedUser(authentication);
    final MeResponse response = new MeResponse(user.userId(), user.accountStatus(), user.roles());
    return ResponseEntity.ok(response);
  }
}
