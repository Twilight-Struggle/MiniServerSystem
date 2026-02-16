package com.example.gateway_bff.service;

import com.example.gateway_bff.model.OidcClaims;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

// Authentication をアプリ内 OidcClaims へ正規化する。
@Component
public class OidcPrincipalMapper {

  public OidcClaims map(Authentication authentication) {
    if (!(authentication instanceof OAuth2AuthenticationToken oauth2Auth)) {
      throw new IllegalArgumentException("authentication must be OAuth2AuthenticationToken");
    }
    if (!(oauth2Auth.getPrincipal() instanceof OidcUser oidcUser)) {
      throw new IllegalArgumentException("principal must be OidcUser");
    }
    final String subject = oidcUser.getSubject();
    if (isBlank(subject)) {
      throw new IllegalArgumentException("sub is required");
    }

    final String provider = oauth2Auth.getAuthorizedClientRegistrationId();
    final String audience = resolveAudience(oidcUser.getClaims());
    final long expiresAtEpochSeconds =
        resolveExpiresAtEpochSeconds(oidcUser.getIdToken().getExpiresAt());
    final java.net.URL issuer = oidcUser.getIssuer();

    return new OidcClaims(
        provider,
        subject,
        oidcUser.getEmail(),
        Boolean.TRUE.equals(oidcUser.getEmailVerified()),
        oidcUser.getFullName(),
        oidcUser.getPicture(),
        issuer == null ? null : issuer.toString(),
        audience,
        expiresAtEpochSeconds,
        oidcUser.getNonce());
  }

  private String resolveAudience(Map<String, Object> claims) {
    final Object aud = claims.get("aud");
    if (aud instanceof String value) {
      return value;
    }
    if (aud instanceof List<?> values && !values.isEmpty()) {
      final Object first = values.getFirst();
      return first == null ? null : String.valueOf(first);
    }
    return null;
  }

  private long resolveExpiresAtEpochSeconds(Instant expiresAt) {
    return expiresAt == null ? 0L : expiresAt.getEpochSecond();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
