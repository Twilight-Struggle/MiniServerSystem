package com.example.gateway_bff.api.response;

import java.util.List;

public record MeResponse(String userId, String accountStatus, List<String> roles) {

  public MeResponse {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
