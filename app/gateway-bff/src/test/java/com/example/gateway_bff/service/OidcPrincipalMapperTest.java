package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class OidcPrincipalMapperTest {

  private final OidcPrincipalMapper mapper = new OidcPrincipalMapper();

  @Test
  void mapRejectsNullAuthentication() {
    assertThatThrownBy(() -> mapper.map(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("authentication must be OAuth2AuthenticationToken");
  }

  @Test
  void mapRejectsNonOAuth2Authentication() {
    final TestingAuthenticationToken auth = new TestingAuthenticationToken("u", "p");

    assertThatThrownBy(() -> mapper.map(auth))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("authentication must be OAuth2AuthenticationToken");
  }

  @Test
  void mapRejectsMissingSubject() {
    final Instant now = Instant.now();
    final OidcIdToken idToken =
        new OidcIdToken(
            "id-token",
            now,
            now.plusSeconds(300),
            Map.of("iss", "http://issuer", "aud", List.of("gateway-bff"), "sub", " "));
    final DefaultOidcUser principal =
        new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
    final OAuth2AuthenticationToken auth =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak");

    assertThatThrownBy(() -> mapper.map(auth))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("sub is required");
  }

  @Test
  void mapExtractsClaims() {
    final Instant now = Instant.now();
    final OidcIdToken idToken =
        new OidcIdToken(
            "id-token",
            now,
            now.plusSeconds(600),
            Map.of(
                "iss", "http://keycloak.localhost/realms/miniserversystem",
                "aud", List.of("gateway-bff"),
                "sub", "ユーザー-1",
                "email", "tést+1@example.com",
                "email_verified", true,
                "name", "山田 太郎",
                "picture", "https://example.com/u.png",
                "nonce", "n-123"));
    final DefaultOidcUser principal =
        new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
    final OAuth2AuthenticationToken auth =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak");

    final var claims = mapper.map(auth);

    assertThat(claims.provider()).isEqualTo("keycloak");
    assertThat(claims.subject()).isEqualTo("ユーザー-1");
    assertThat(claims.email()).isEqualTo("tést+1@example.com");
    assertThat(claims.emailVerified()).isTrue();
    assertThat(claims.name()).isEqualTo("山田 太郎");
    assertThat(claims.picture()).isEqualTo("https://example.com/u.png");
    assertThat(claims.issuer()).contains("keycloak.localhost");
    assertThat(claims.audience()).isEqualTo("gateway-bff");
    assertThat(claims.expiresAtEpochSeconds()).isEqualTo(now.plusSeconds(600).getEpochSecond());
    assertThat(claims.nonce()).isEqualTo("n-123");
  }
}
