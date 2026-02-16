package com.example.gateway_bff.api.response;

import java.util.Map;

public record ProfileAggregateResponse(
    Map<String, Object> account, Map<String, Object> entitlement, Map<String, Object> matchmaking) {

  public ProfileAggregateResponse {
    account = account == null ? Map.of() : Map.copyOf(account);
    entitlement = entitlement == null ? Map.of() : Map.copyOf(entitlement);
    matchmaking = matchmaking == null ? Map.of() : Map.copyOf(matchmaking);
  }
}
