package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.gateway_bff.model.OidcClaims;
import org.junit.jupiter.api.Test;

class OidcTokenVerifierTest {

  @Test
  void verifyRejectsBlankToken() {
    final OidcTokenVerifier verifier =
        new OidcTokenVerifier(
            "keycloak", "http://keycloak.localhost/realms/miniserversystem", "gateway-bff");
    assertThatThrownBy(() -> verifier.verify("", "nonce"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verifyReturnsClaims() {
    final OidcTokenVerifier verifier =
        new OidcTokenVerifier(
            "keycloak", "http://keycloak.localhost/realms/miniserversystem", "gateway-bff");
    final OidcClaims claims = verifier.verify("token", "nonce");
    assertThat(claims.provider()).isEqualTo("keycloak");
    assertThat(claims.issuer()).isEqualTo("http://keycloak.localhost/realms/miniserversystem");
    assertThat(claims.audience()).isEqualTo("gateway-bff");
    assertThat(claims.nonce()).isEqualTo("nonce");
  }
}
