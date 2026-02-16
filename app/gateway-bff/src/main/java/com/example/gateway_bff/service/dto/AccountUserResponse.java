package com.example.gateway_bff.service.dto;

import java.util.List;

public record AccountUserResponse(
    String userId, String displayName, String locale, String status, List<String> roles) {

  public AccountUserResponse {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
