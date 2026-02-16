package com.example.gateway_bff.service.dto;

import java.util.List;

public record AccountIdentityResolveResponse(
    String userId, String accountStatus, List<String> roles) {

  public AccountIdentityResolveResponse {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
