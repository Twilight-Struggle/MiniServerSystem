/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/api/ProfileAggregateController.java
 * 何を: 複数サービスのデータを集約する API を提供
 * なぜ: クライアントからの多重呼び出しを減らし、BFF の責務を果たすため
 */
package com.example.gateway_bff.api;

import com.example.gateway_bff.api.response.ProfileAggregateResponse;
import com.example.gateway_bff.service.ProfileAggregateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ProfileAggregateController {

    private final ProfileAggregateService profileAggregateService;

    public ProfileAggregateController(ProfileAggregateService profileAggregateService) {
        this.profileAggregateService = profileAggregateService;
    }

    /**
     * 役割:
     * - userId をキーに Account/Entitlement/Matchmaking 情報を統合して返す。
     *
     * 期待動作:
     * - 呼び出し元の認証状態を前提に、対象 userId の集約データを返す。
     * - 部分失敗時のフォールバック方針(空配列/部分データ/エラー)を仕様として固定する。
     */
    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<ProfileAggregateResponse> getProfile(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(profileAggregateService.aggregateByUserId(userId));
    }
}
