/*
 * どこで: app/account/src/main/java/com/example/account/api/AccountIdentityController.java
 * 何を: identity 解決 API を提供するコントローラー
 * なぜ: BFF からの同定リクエストの入口を明確化するため
 */
package com.example.account.api;

import com.example.account.api.request.IdentityResolveRequest;
import com.example.account.api.response.IdentityResolveResponse;
import com.example.account.service.IdentityResolveService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AccountIdentityController {

    private final IdentityResolveService identityResolveService;

    public AccountIdentityController(IdentityResolveService identityResolveService) {
        this.identityResolveService = identityResolveService;
    }

    /**
     * 役割:
     * - OIDC claims 相当の入力を受け取り、内部 userId を解決/作成する。
     *
     * 期待動作:
     * - provider + subject を必須キーとして受理する。
     * - Service の結果をそのまま返却し、BFF がセッション化できる形を保証する。
     */
    @PostMapping("/identities:resolve")
    public ResponseEntity<IdentityResolveResponse> resolveIdentity(@RequestBody IdentityResolveRequest request) {
        IdentityResolveResponse response = identityResolveService.resolve(request);
        return ResponseEntity.ok(response);
    }
}
