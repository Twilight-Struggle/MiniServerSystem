package com.example.account.config;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public class UserOwnershipAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {

  @Override
  public AuthorizationDecision check(
      Supplier<Authentication> authentication, RequestAuthorizationContext context) {
    final Authentication auth = authentication.get();
    if (auth == null || !auth.isAuthenticated()) {
      return new AuthorizationDecision(false);
    }
    if (hasRole(auth, "ROLE_ADMIN")) {
      return new AuthorizationDecision(true);
    }
    final String targetUserId = context.getVariables().get("userId");
    if (targetUserId == null || targetUserId.isBlank()) {
      return new AuthorizationDecision(false);
    }
    return new AuthorizationDecision(targetUserId.equals(auth.getName()));
  }

  private boolean hasRole(Authentication auth, String role) {
    return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
  }
}
