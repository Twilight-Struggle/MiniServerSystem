package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import org.junit.jupiter.api.Test;

class AccountResolveClientTest {

  @Test
  void resolveRejectsNullClaims() {
    final AccountResolveClient client = new AccountResolveClient();
    assertThatThrownBy(() -> client.resolveIdentity(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("claims is required");
  }

  @Test
  void resolveRejectsBlankProvider() {
    final AccountResolveClient client = new AccountResolveClient();
    final OidcClaims claims =
        new OidcClaims(" ", "sub-x", "a@example.com", true, "n", null, "iss", "aud", 1L, null);

    assertThatThrownBy(() -> client.resolveIdentity(claims))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("provider is required");
  }

  @Test
  void resolveRejectsBlankSubject() {
    final AccountResolveClient client = new AccountResolveClient();
    final OidcClaims claims =
        new OidcClaims("keycloak", "", "a@example.com", true, "n", null, "iss", "aud", 1L, null);

    assertThatThrownBy(() -> client.resolveIdentity(claims))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("subject is required");
  }

  @Test
  void resolveReturnsAuthenticatedUser() {
    final AccountResolveClient client = new AccountResolveClient();
    final OidcClaims claims =
        new OidcClaims(
            "keycloak",
            "ユーザー-01",
            "a+テスト@example.com",
            false,
            "テストユーザー",
            "https://example.com/p.png",
            "http://keycloak.localhost/realms/miniserversystem",
            "gateway-bff",
            1L,
            null);

    final AuthenticatedUser user = client.resolveIdentity(claims);

    assertThat(user.userId()).isEqualTo("ユーザー-01");
    assertThat(user.accountStatus()).isEqualTo("ACTIVE");
    assertThat(user.roles()).containsExactly("USER");
  }
}
