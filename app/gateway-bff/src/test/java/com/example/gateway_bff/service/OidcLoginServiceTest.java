package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.gateway_bff.api.response.LoginRedirectResponse;
import org.junit.jupiter.api.Test;

class OidcLoginServiceTest {

  @Test
  void prepareLoginReturnsAuthorizationUrlAndState() {
    final OidcLoginService service =
        new OidcLoginService(
            "http://keycloak.localhost/realms/miniserversystem/protocol/openid-connect/auth",
            "gateway-bff",
            "http://localhost:18080/callback",
            "openid profile email");

    final LoginRedirectResponse response = service.prepareLogin();

    assertThat(response.state()).isNotBlank();
    assertThat(response.authorizationUrl())
        .contains("http://keycloak.localhost/realms/miniserversystem/protocol/openid-connect/auth");
    assertThat(response.authorizationUrl()).contains("response_type=code");
    assertThat(response.authorizationUrl()).contains("scope=openid");
  }
}
