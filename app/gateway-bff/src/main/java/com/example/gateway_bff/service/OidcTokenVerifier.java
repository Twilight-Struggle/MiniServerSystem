package com.example.gateway_bff.service;

import com.example.gateway_bff.model.OidcClaims;
import org.springframework.stereotype.Service;

// 旧トークン検証互換。削除予定。
@Service
@Deprecated(forRemoval = true)
public class OidcTokenVerifier {

  public OidcTokenVerifier() {}

  public OidcTokenVerifier(String provider, String issuer, String clientId) {
    // テスト互換のため維持。
  }

  public OidcClaims verify(String idToken, String expectedNonce) {
    throw new UnsupportedOperationException("Token verification is delegated to Spring Security");
  }
}
