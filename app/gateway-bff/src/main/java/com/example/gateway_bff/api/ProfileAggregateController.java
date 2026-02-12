package com.example.gateway_bff.api;

import com.example.gateway_bff.api.response.ProfileAggregateResponse;
import com.example.gateway_bff.service.ProfileAggregateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ProfileAggregateController {

  private final ProfileAggregateService profileAggregateService;

  @GetMapping("/users/{userId}/profile")
  public ResponseEntity<ProfileAggregateResponse> getProfile(
      @PathVariable("userId") String userId) {
    return ResponseEntity.ok(profileAggregateService.aggregateByUserId(userId));
  }
}
