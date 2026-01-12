/*
 * どこで: Entitlement API
 * 何を: 権利付与/剥奪/参照のエンドポイントを提供する
 * なぜ: アプリの公開インターフェースを明確にするため
 */
package com.example.entitlement.api;

import com.example.entitlement.service.EntitlementService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Validated
public class EntitlementController {

    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    private final EntitlementService entitlementService;

    @PostMapping("/entitlements/grants")
    public ResponseEntity<EntitlementResponse> grant(
            @RequestHeader(value = HEADER_IDEMPOTENCY_KEY)
            @NotBlank(message = "Idempotency-Key is required")
            String idempotencyKey,
            @RequestHeader(value = HEADER_TRACE_ID, required = false) String traceId,
            @Valid @RequestBody EntitlementRequest request) {
        EntitlementResponse response = entitlementService.grant(request, idempotencyKey, traceId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/entitlements/revokes")
    public ResponseEntity<EntitlementResponse> revoke(
            @RequestHeader(value = HEADER_IDEMPOTENCY_KEY)
            @NotBlank(message = "Idempotency-Key is required")
            String idempotencyKey,
            @RequestHeader(value = HEADER_TRACE_ID, required = false) String traceId,
            @Valid @RequestBody EntitlementRequest request) {
        EntitlementResponse response = entitlementService.revoke(request, idempotencyKey, traceId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{user_id}/entitlements")
    public EntitlementsResponse list(
            @PathVariable("user_id")
            @NotBlank(message = "user_id is required")
            String userId) {
        return entitlementService.listByUser(userId);
    }
}
