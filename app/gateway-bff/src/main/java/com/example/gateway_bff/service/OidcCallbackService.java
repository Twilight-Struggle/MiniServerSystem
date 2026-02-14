package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 旧コールバックAPI互換。削除予定。
@Service
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
public class OidcCallbackService {

  private final OidcAuthenticatedUserService oidcAuthenticatedUserService;

  public AuthenticatedUser handleCallback(String state, String code) {
    throw new UnsupportedOperationException("Callback flow is handled by Spring Security");
  }
}
