package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountResolveClient {

  public AuthenticatedUser resolveIdentity(OidcClaims claims) {
    if (claims == null) {
      throw new IllegalArgumentException("claims is required");
    }
    return new AuthenticatedUser(claims.subject(), "ACTIVE", List.of("USER"));
  }
}
