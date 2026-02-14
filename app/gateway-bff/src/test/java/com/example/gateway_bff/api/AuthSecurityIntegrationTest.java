package com.example.gateway_bff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void unauthenticatedMeReturns401() throws Exception {
    mockMvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedMeReturns200() throws Exception {
    mockMvc
        .perform(get("/v1/me").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("user"))
        .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
  }

  @Test
  void loginEndpointReturns302() throws Exception {
    mockMvc.perform(get("/login")).andExpect(status().isFound());
  }

  @Test
  void logoutWithoutCsrfReturns403() throws Exception {
    mockMvc.perform(post("/logout")).andExpect(status().isForbidden());
  }

  @Test
  void logoutWithCsrfReturns204() throws Exception {
    mockMvc.perform(post("/logout").with(csrf())).andExpect(status().isNoContent());
  }

  @Test
  void errorEndpointIsAccessibleWithoutAuthentication() throws Exception {
    final int statusCode = mockMvc.perform(get("/error")).andReturn().getResponse().getStatus();
    assertThat(statusCode).isNotEqualTo(401);
  }
}
