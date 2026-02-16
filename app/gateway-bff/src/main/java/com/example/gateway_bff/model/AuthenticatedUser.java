package com.example.gateway_bff.model;

import java.util.List;

public record AuthenticatedUser(String userId, String accountStatus, List<String> roles) {

  public AuthenticatedUser {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
