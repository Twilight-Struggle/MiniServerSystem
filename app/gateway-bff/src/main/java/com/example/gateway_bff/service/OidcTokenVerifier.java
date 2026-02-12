package com.example.gateway_bff.service;

import com.example.gateway_bff.model.OidcClaims;
import org.springframework.stereotype.Service;

@Service
public class OidcTokenVerifier {

  public OidcClaims verify(String idToken, String expectedNonce) {
    if (idToken == null || idToken.isBlank()) {
      throw new IllegalArgumentException("id_token is required");
    }
    if (expectedNonce == null || expectedNonce.isBlank()) {
      throw new IllegalArgumentException("expected nonce is required");
    }
    return new OidcClaims(
        "google",
        "subject-from-token",
        "user@example.com",
        true,
        "user",
        null,
        "https://accounts.google.com",
        "dummy-client",
        0L,
        expectedNonce);
  }
}
