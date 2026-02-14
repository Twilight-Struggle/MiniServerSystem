package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class OidcCallbackServiceTest {

  @Test
  void handleCallbackThrowsUnsupportedOperation() {
    final OidcAuthenticatedUserService delegate = mock(OidcAuthenticatedUserService.class);
    final OidcCallbackService service = new OidcCallbackService(delegate);

    assertThatThrownBy(() -> service.handleCallback("state", "code"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
