package com.example.gateway_bff.service;

import com.example.gateway_bff.api.response.LoginRedirectResponse;
import org.springframework.stereotype.Service;

// 旧ログインAPI互換。削除予定。
@Service
public class OidcLoginService {

  public OidcLoginService() {}

  public OidcLoginService(
      String authorizationEndpoint, String clientId, String redirectUri, String scope) {
    // テスト互換のため維持。
  }

  public LoginRedirectResponse prepareLogin() {
    throw new UnsupportedOperationException("Legacy JSON login flow has been retired");
  }
}
