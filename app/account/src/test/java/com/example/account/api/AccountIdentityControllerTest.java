package com.example.account.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.account.api.request.IdentityResolveRequest;
import com.example.account.api.response.IdentityResolveResponse;
import com.example.account.service.IdentityResolveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountIdentityController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AccountApiExceptionHandler.class)
class AccountIdentityControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private IdentityResolveService identityResolveService;

  @Test
  void resolveReturnsOk() throws Exception {
    when(identityResolveService.resolve(any(IdentityResolveRequest.class)))
        .thenReturn(new IdentityResolveResponse("user-1", "ACTIVE", List.of("USER")));

    final String body =
        objectMapper.writeValueAsString(
            new IdentityResolveRequest("google", "sub", "a@example.com", true, "n", "p"));

    mockMvc
        .perform(post("/identities:resolve").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("user-1"));
  }
}
