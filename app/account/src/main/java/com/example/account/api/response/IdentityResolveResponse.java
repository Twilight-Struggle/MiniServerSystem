package com.example.account.api.response;

import java.util.List;

public record IdentityResolveResponse(String userId, String accountStatus, List<String> roles) {

  public IdentityResolveResponse {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
