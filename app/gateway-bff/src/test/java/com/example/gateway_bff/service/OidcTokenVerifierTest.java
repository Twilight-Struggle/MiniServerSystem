package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.gateway_bff.model.OidcClaims;
import org.junit.jupiter.api.Test;

class OidcTokenVerifierTest {

  @Test
  void verifyRejectsBlankToken() {
    final OidcTokenVerifier verifier = new OidcTokenVerifier();
    assertThatThrownBy(() -> verifier.verify("", "nonce"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verifyReturnsClaims() {
    final OidcTokenVerifier verifier = new OidcTokenVerifier();
    final OidcClaims claims = verifier.verify("token", "nonce");
    assertThat(claims.provider()).isEqualTo("google");
    assertThat(claims.nonce()).isEqualTo("nonce");
  }
}
