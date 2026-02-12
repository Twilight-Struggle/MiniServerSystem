package com.example.account.api;

import com.example.account.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AccountAdminController {

  private static final String HEADER_ACTOR_USER_ID = "X-Actor-User-Id";
  private final AdminUserService adminUserService;

  @PostMapping("/{userId}:suspend")
  public ResponseEntity<Void> suspendUser(
      @PathVariable("userId") String userId,
      @RequestHeader(value = HEADER_ACTOR_USER_ID, required = false) String actorUserId,
      @RequestParam(value = "reason", required = false) String reason) {
    adminUserService.suspendUser(actorUserId, userId, reason);
    return ResponseEntity.noContent().build();
  }
}
