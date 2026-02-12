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
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolveReturnsAuthenticatedUser() {
    final AccountResolveClient client = new AccountResolveClient();
    final OidcClaims claims =
        new OidcClaims(
            "google", "sub-x", "a@example.com", true, "n", "p", "iss", "aud", 1L, "nonce");

    final AuthenticatedUser user = client.resolveIdentity(claims);

    assertThat(user.userId()).isEqualTo("sub-x");
    assertThat(user.roles()).containsExactly("USER");
  }
}
