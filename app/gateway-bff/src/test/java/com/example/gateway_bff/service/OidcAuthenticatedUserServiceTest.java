package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class OidcAuthenticatedUserServiceTest {

  @Test
  void resolveReturnsActiveUser() {
    final OidcPrincipalMapper mapper = mock(OidcPrincipalMapper.class);
    final AccountResolveClient client = mock(AccountResolveClient.class);
    final OidcAuthenticatedUserService service = new OidcAuthenticatedUserService(mapper, client);
    final Authentication auth = mock(Authentication.class);
    final OidcClaims claims =
        new OidcClaims(
            "keycloak", "sub-1", "a@example.com", true, "n", null, "iss", "aud", 1L, null);
    when(mapper.map(auth)).thenReturn(claims);
    when(client.resolveIdentity(claims))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));

    final AuthenticatedUser result = service.resolveAuthenticatedUser(auth);

    assertThat(result.userId()).isEqualTo("user-1");
  }

  @Test
  void resolveRejectsNonActiveUser() {
    final OidcPrincipalMapper mapper = mock(OidcPrincipalMapper.class);
    final AccountResolveClient client = mock(AccountResolveClient.class);
    final OidcAuthenticatedUserService service = new OidcAuthenticatedUserService(mapper, client);
    final Authentication auth = mock(Authentication.class);
    final OidcClaims claims =
        new OidcClaims(
            "keycloak", "sub-1", "a@example.com", true, "n", null, "iss", "aud", 1L, null);
    when(mapper.map(auth)).thenReturn(claims);
    when(client.resolveIdentity(claims))
        .thenReturn(new AuthenticatedUser("user-1", "SUSPENDED", List.of("USER")));

    assertThatThrownBy(() -> service.resolveAuthenticatedUser(auth))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("account is not active");
  }
}
