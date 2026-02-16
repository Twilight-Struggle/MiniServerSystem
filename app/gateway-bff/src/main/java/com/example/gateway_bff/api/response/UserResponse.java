package com.example.gateway_bff.api.response;

import java.util.List;

public record UserResponse(
    String userId, String displayName, String locale, String status, List<String> roles) {

  public UserResponse {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
