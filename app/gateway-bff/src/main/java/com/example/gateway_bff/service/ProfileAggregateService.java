package com.example.gateway_bff.service;

import com.example.gateway_bff.api.response.ProfileAggregateResponse;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProfileAggregateService {

  public ProfileAggregateResponse aggregateByUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId is required");
    }
    return new ProfileAggregateResponse(Map.of("user_id", userId), Map.of(), Map.of());
  }
}
