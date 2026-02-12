/*
 * どこで: app/account/src/main/java/com/example/account/api/AccountUserController.java
 * 何を: 一般ユーザー情報 API を提供するコントローラー
 * なぜ: 参照/更新の契約を BFF から利用可能にするため
 */
package com.example.account.api;

import com.example.account.api.request.UserPatchRequest;
import com.example.account.api.response.UserResponse;
import com.example.account.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class AccountUserController {

    private final UserService userService;

    public AccountUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 役割:
     * - userId 指定でユーザー情報を取得する。
     *
     * 期待動作:
     * - roles と status を含む認可判断可能な形で返却する。
     * - 対象不存在時は 404 へマッピングする。
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    /**
     * 役割:
     * - displayName/locale の最小更新を受け付ける。
     *
     * 期待動作:
     * - 入力仕様に違反する値は 400 とする。
     * - 更新後の最新ユーザー情報を返却する。
     */
    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> patchUser(
            @PathVariable("userId") String userId,
            @RequestBody UserPatchRequest request) {
        return ResponseEntity.ok(userService.patchUser(userId, request));
    }
}
