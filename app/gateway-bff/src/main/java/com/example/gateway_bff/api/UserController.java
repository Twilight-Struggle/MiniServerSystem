package com.example.gateway_bff.api;

import com.example.gateway_bff.api.request.UserPatchRequest;
import com.example.gateway_bff.api.response.UserResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.AccountUserClient;
import com.example.gateway_bff.service.OidcAuthenticatedUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final OidcAuthenticatedUserService oidcAuthenticatedUserService;
  private final AccountUserClient accountUserClient;

  @GetMapping("/{userId}")
  public ResponseEntity<UserResponse> getUser(
      @PathVariable("userId") String userId, Authentication authentication) {
    final AuthenticatedUser requester =
        oidcAuthenticatedUserService.resolveAuthenticatedUser(authentication);
    return ResponseEntity.ok(accountUserClient.getUser(userId, requester));
  }

  @PatchMapping("/{userId}")
  public ResponseEntity<UserResponse> patchUser(
      @PathVariable("userId") String userId,
      @RequestBody UserPatchRequest request,
      Authentication authentication) {
    final AuthenticatedUser requester =
        oidcAuthenticatedUserService.resolveAuthenticatedUser(authentication);
    return ResponseEntity.ok(accountUserClient.patchUser(userId, request, requester));
  }
}
