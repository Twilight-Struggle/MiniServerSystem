package com.example.gateway_bff.service;

import com.example.gateway_bff.api.response.LoginRedirectResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OidcLoginService {

  private final String authorizationEndpoint;
  private final String clientId;
  private final String redirectUri;
  private final String scope;

  public OidcLoginService(
      @Value("${oidc.authorization-endpoint}") String authorizationEndpoint,
      @Value("${oidc.client-id:dummy-client}") String clientId,
      @Value("${oidc.redirect-uri:http://localhost:18080/callback}") String redirectUri,
      @Value("${oidc.scope:openid profile email}") String scope) {
    this.authorizationEndpoint = authorizationEndpoint;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.scope = scope;
  }

  public LoginRedirectResponse prepareLogin() {
    final String state = UUID.randomUUID().toString();
    final String nonce = UUID.randomUUID().toString();
    final String encodedRedirectUri = encode(redirectUri);
    final String url =
        authorizationEndpoint
            + "?response_type=code"
            + "&scope="
            + encode(scope)
            + "&client_id="
            + encode(clientId)
            + "&redirect_uri="
            + encodedRedirectUri
            + "&state="
            + encode(state)
            + "&nonce="
            + encode(nonce);
    return new LoginRedirectResponse(url, state);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
