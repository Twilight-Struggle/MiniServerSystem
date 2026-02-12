/*
 * どこで: app/account/src/main/java/com/example/account/api/AccountAdminController.java
 * 何を: 管理者向けアカウント操作 API を提供
 * なぜ: 高権限操作を明示的な経路に分離するため
 */
package com.example.account.api;

import com.example.account.service.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AccountAdminController {

    private static final String HEADER_ACTOR_USER_ID = "X-Actor-User-Id";
    private final AdminUserService adminUserService;

    public AccountAdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 役割:
     * - 対象ユーザーを停止し、監査ログへ操作記録を残す。
     *
     * 期待動作:
     * - actorUserId は認証済み管理者の ID をヘッダーやコンテキストから受け取る。
     * - 呼び出し権限の判定は Security 設定側で実施し、本メソッドは業務処理に専念する。
     */
    @PostMapping("/{userId}:suspend")
    public ResponseEntity<Void> suspendUser(
            @PathVariable("userId") String userId,
            @RequestHeader(value = HEADER_ACTOR_USER_ID, required = false) String actorUserId,
            @RequestParam(value = "reason", required = false) String reason) {
        adminUserService.suspendUser(actorUserId, userId, reason);
        return ResponseEntity.noContent().build();
    }
}
