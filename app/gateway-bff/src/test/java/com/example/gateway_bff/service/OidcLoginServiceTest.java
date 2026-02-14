package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OidcLoginServiceTest {

  @Test
  void prepareLoginThrowsUnsupportedOperation() {
    final OidcLoginService service = new OidcLoginService();
    assertThatThrownBy(service::prepareLogin).isInstanceOf(UnsupportedOperationException.class);
  }
}
