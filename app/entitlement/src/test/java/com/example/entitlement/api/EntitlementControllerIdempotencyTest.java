/*
 * どこで: Entitlement API のWeb層テスト
 * 何を: Idempotency-Key 競合時の HTTP 409 応答を検証する
 * なぜ: 同一キーで異なるリクエストが来た場合に仕様通りのエラーを返すため
 */
package com.example.entitlement.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.entitlement.service.EntitlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EntitlementController.class)
@Import(ApiExceptionHandler.class)
class EntitlementControllerIdempotencyTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EntitlementService entitlementService;

  @Test
  void grantReturnsConflictWhenIdempotencyKeyHashMismatch() throws Exception {
    final String body =
        """
        {
          "user_id": "user-1",
          "stock_keeping_unit": "sku-1",
          "reason": "purchase",
          "purchase_id": "purchase-1"
        }
        """;

    // サービス層の競合例外が 409 + IDEMPOTENCY_KEY_CONFLICT に変換される前提を固定する。
    when(entitlementService.grant(
            any(EntitlementRequest.class), eq("idem-conflict"), nullable(String.class)))
        .thenThrow(new IdempotencyConflictException("Idempotency-Key conflict"));

    mockMvc
        .perform(
            post("/v1/entitlements/grants")
                .header("Idempotency-Key", "idem-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"))
        .andExpect(jsonPath("$.message").value("Idempotency-Key conflict"));
  }
}
