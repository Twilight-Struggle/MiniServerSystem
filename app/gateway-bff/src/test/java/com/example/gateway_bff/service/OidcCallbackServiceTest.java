package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OidcCallbackServiceTest {

  @Mock private OidcTokenVerifier tokenVerifier;
  @Mock private AccountResolveClient accountResolveClient;

  @InjectMocks private OidcCallbackService service;

  @Test
  void handleCallbackRejectsBlankState() {
    assertThatThrownBy(() -> service.handleCallback("", "code"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void handleCallbackResolvesUser() {
    final OidcClaims claims =
        new OidcClaims("google", "sub", "a@example.com", true, "n", "p", "iss", "aud", 1L, "nonce");
    final AuthenticatedUser user =
        new AuthenticatedUser("user-1", "ACTIVE", java.util.List.of("USER"));
    when(tokenVerifier.verify("code", "state")).thenReturn(claims);
    when(accountResolveClient.resolveIdentity(claims)).thenReturn(user);

    final AuthenticatedUser result = service.handleCallback("state", "code");

    assertThat(result.userId()).isEqualTo("user-1");
  }
}
