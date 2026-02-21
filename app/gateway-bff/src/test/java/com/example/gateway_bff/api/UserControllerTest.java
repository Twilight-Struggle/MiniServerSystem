package com.example.gateway_bff.api;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gateway_bff.api.request.UserPatchRequest;
import com.example.gateway_bff.api.response.UserResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.AccountIntegrationException;
import com.example.gateway_bff.service.AccountUserClient;
import com.example.gateway_bff.service.OidcAuthenticatedUserService;
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

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GatewayApiExceptionHandler.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private OidcAuthenticatedUserService oidcAuthenticatedUserService;
  @MockitoBean private AccountUserClient accountUserClient;

  @Test
  void getUserReturns200() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(accountUserClient.getUser(any(), any()))
        .thenReturn(new UserResponse("user-1", "name", "ja", "ACTIVE", List.of("USER")));

    mockMvc
        .perform(get("/v1/users/user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("user-1"))
        .andExpect(jsonPath("$.displayName").value("name"));
  }

  @Test
  void patchUserReturns200() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(accountUserClient.patchUser(any(), any(UserPatchRequest.class), any()))
        .thenReturn(new UserResponse("user-1", "new", "en", "ACTIVE", List.of("USER")));

    mockMvc
        .perform(
            patch("/v1/users/user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UserPatchRequest("new", "en"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("new"))
        .andExpect(jsonPath("$.locale").value("en"));
  }

  @Test
  void getUserReturns404WhenAccountUserNotFound() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(accountUserClient.getUser(any(), any()))
        .thenThrow(
            new AccountIntegrationException(
                AccountIntegrationException.Reason.NOT_FOUND, "account user not found"));

    mockMvc
        .perform(get("/v1/users/user-999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
  }

  @Test
  void getUserReturns403WhenAccountForbidden() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(accountUserClient.getUser(any(), any()))
        .thenThrow(
            new AccountIntegrationException(
                AccountIntegrationException.Reason.FORBIDDEN, "account denied access"));

    mockMvc
        .perform(get("/v1/users/user-2"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_FORBIDDEN"));
  }
}
