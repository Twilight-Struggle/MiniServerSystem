package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OidcCallbackService {

  private final OidcTokenVerifier tokenVerifier;
  private final AccountResolveClient accountResolveClient;

  public AuthenticatedUser handleCallback(String state, String code) {
    if (state == null || state.isBlank()) {
      throw new IllegalArgumentException("state is required");
    }
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code is required");
    }
    final OidcClaims claims = tokenVerifier.verify(code, state);
    final AuthenticatedUser user = accountResolveClient.resolveIdentity(claims);
    if (!"ACTIVE".equals(user.accountStatus())) {
      throw new IllegalArgumentException("account is not active");
    }
    return user;
  }
}
