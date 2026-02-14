package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OidcTokenVerifierTest {

  @Test
  void verifyThrowsUnsupportedOperation() {
    final OidcTokenVerifier verifier = new OidcTokenVerifier();
    assertThatThrownBy(() -> verifier.verify("token", "nonce"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
