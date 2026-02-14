package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

// 認証 principal を業務ユーザーへ解決する。
@Service
@RequiredArgsConstructor
public class OidcAuthenticatedUserService {

  private final OidcPrincipalMapper oidcPrincipalMapper;
  private final AccountResolveClient accountResolveClient;

  public AuthenticatedUser resolveAuthenticatedUser(Authentication authentication) {
    final OidcClaims claims = oidcPrincipalMapper.map(authentication);
    final AuthenticatedUser user = accountResolveClient.resolveIdentity(claims);
    if (!"ACTIVE".equals(user.accountStatus())) {
      throw new IllegalStateException("account is not active");
    }
    return user;
  }
}
