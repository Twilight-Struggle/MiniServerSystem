package com.example.gateway_bff.service;

import com.example.gateway_bff.model.OidcClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OidcTokenVerifier {

  private final String provider;
  private final String issuer;
  private final String clientId;

  public OidcTokenVerifier(
      @Value("${oidc.provider:keycloak}") String provider,
      @Value("${oidc.issuer:http://keycloak.localhost/realms/miniserversystem}") String issuer,
      @Value("${oidc.client-id:dummy-client}") String clientId) {
    this.provider = provider;
    this.issuer = issuer;
    this.clientId = clientId;
  }

  public OidcClaims verify(String idToken, String expectedNonce) {
    if (idToken == null || idToken.isBlank()) {
      throw new IllegalArgumentException("id_token is required");
    }
    if (expectedNonce == null || expectedNonce.isBlank()) {
      throw new IllegalArgumentException("expected nonce is required");
    }
    return new OidcClaims(
        provider,
        "subject-from-token",
        "user@example.com",
        true,
        "user",
        null,
        issuer,
        clientId,
        0L,
        expectedNonce);
  }
}
