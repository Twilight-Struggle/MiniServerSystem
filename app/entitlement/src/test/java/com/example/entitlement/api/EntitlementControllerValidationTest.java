/*
 * どこで: Entitlement API のWeb層テスト
 * 何を: 入力検証の標準例外が ApiErrorResponse に変換されることを検証する
 * なぜ: クライアントに一貫した BAD_REQUEST 応答を返すため
 */
package com.example.entitlement.api;

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
class EntitlementControllerValidationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EntitlementService entitlementService;

  @Test
  void grantReturnsBadRequestWhenIdempotencyKeyMissing() throws Exception {
    final String body =
        """
        {
          "user_id": "user-1",
          "stock_keeping_unit": "sku-1",
          "reason": "purchase",
          "purchase_id": "purchase-1"
        }
        """;

    mockMvc
        .perform(
            post("/v1/entitlements/grants").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("Idempotency-Key is required"));
  }

  @Test
  void grantReturnsBadRequestWhenUserIdBlank() throws Exception {
    final String body =
        """
        {
          "user_id": "",
          "stock_keeping_unit": "sku-1",
          "reason": "purchase",
          "purchase_id": "purchase-1"
        }
        """;

    mockMvc
        .perform(
            post("/v1/entitlements/grants")
                .header("Idempotency-Key", "idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("user_id is required"));
  }
}
