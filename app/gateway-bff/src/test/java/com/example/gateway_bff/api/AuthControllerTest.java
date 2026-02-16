package com.example.gateway_bff.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.AccountInactiveException;
import com.example.gateway_bff.service.OidcAuthenticatedUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GatewayApiExceptionHandler.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OidcAuthenticatedUserService oidcAuthenticatedUserService;

  @Test
  void loginReturns302ToAuthorizationEndpoint() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/oauth2/authorization/keycloak"));
  }

  @Test
  void loginWithErrorReturns401WithoutRedirect() throws Exception {
    mockMvc.perform(get("/login").param("error", "true")).andExpect(status().isUnauthorized());
  }

  @Test
  void meReturnsAuthenticatedUser() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));

    mockMvc
        .perform(get("/v1/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("user-1"))
        .andExpect(jsonPath("$.roles[0]").value("USER"));
  }

  @Test
  void meReturns403WhenAccountIsSuspended() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new AccountInactiveException("account is not active"));

    mockMvc.perform(get("/v1/me")).andExpect(status().isForbidden());
  }
}
