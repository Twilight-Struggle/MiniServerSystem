package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OidcCallbackServiceTest {

  @Test
  void handleCallbackThrowsUnsupportedOperation() {
    final OidcCallbackService service = new OidcCallbackService();

    assertThatThrownBy(() -> service.handleCallback("state", "code"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
