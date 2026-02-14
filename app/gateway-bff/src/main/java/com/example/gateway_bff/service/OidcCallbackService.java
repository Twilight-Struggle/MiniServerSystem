package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import org.springframework.stereotype.Service;

// 旧コールバックAPI互換。削除予定。
@Service
@Deprecated(forRemoval = true)
public class OidcCallbackService {

  public AuthenticatedUser handleCallback(String state, String code) {
    throw new UnsupportedOperationException("Callback flow is handled by Spring Security");
  }
}
