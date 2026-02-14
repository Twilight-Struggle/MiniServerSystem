package com.example.account.api;

import com.example.account.api.request.UserPatchRequest;
import com.example.account.api.response.UserResponse;
import com.example.account.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class AccountUserController {

  private final UserService userService;

  @GetMapping("/{userId}")
  public ResponseEntity<UserResponse> getUser(@PathVariable("userId") String userId) {
    return ResponseEntity.ok(userService.getUser(userId));
  }

  @PatchMapping("/{userId}")
  public ResponseEntity<UserResponse> patchUser(
      @PathVariable("userId") String userId, @RequestBody UserPatchRequest request) {
    return ResponseEntity.ok(userService.patchUser(userId, request));
  }
}
