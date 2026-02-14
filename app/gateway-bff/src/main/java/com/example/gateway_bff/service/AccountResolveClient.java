package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import java.util.List;
import org.springframework.stereotype.Service;

// OIDC claims から account 解決結果を返す（暫定ローカル実装）。
@Service
public class AccountResolveClient {

  public AuthenticatedUser resolveIdentity(OidcClaims claims) {
    if (claims == null) {
      throw new IllegalArgumentException("claims is required");
    }
    if (isBlank(claims.provider())) {
      throw new IllegalArgumentException("provider is required");
    }
    if (isBlank(claims.subject())) {
      throw new IllegalArgumentException("subject is required");
    }
    return new AuthenticatedUser(claims.subject(), "ACTIVE", List.of("USER"));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
