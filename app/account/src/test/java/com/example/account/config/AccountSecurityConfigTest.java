package com.example.account.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.account.api.AccountAdminController;
import com.example.account.api.AccountIdentityController;
import com.example.account.api.AccountUserController;
import com.example.account.api.request.IdentityResolveRequest;
import com.example.account.api.request.UserPatchRequest;
import com.example.account.api.response.IdentityResolveResponse;
import com.example.account.api.response.UserResponse;
import com.example.account.service.AdminUserService;
import com.example.account.service.IdentityResolveService;
import com.example.account.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
  AccountIdentityController.class,
  AccountUserController.class,
  AccountAdminController.class
})
@AutoConfigureMockMvc
@Import(AccountSecurityConfig.class)
@TestPropertySource(properties = "account.internal-api.token=test-internal-token")
class AccountSecurityConfigTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private IdentityResolveService identityResolveService;
  @MockitoBean private UserService userService;
  @MockitoBean private AdminUserService adminUserService;

  @Test
  void resolveIdentityRejectsWhenNoInternalToken() throws Exception {
    final String body =
        objectMapper.writeValueAsString(
            new IdentityResolveRequest("keycloak", "sub", "a@example.com", true, "n", "p"));

    mockMvc
        .perform(post("/identities:resolve").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void resolveIdentityAllowsWhenValidInternalToken() throws Exception {
    when(identityResolveService.resolve(any(IdentityResolveRequest.class)))
        .thenReturn(new IdentityResolveResponse("user-1", "ACTIVE", List.of("USER")));
    final String body =
        objectMapper.writeValueAsString(
            new IdentityResolveRequest("keycloak", "sub", "a@example.com", true, "n", "p"));

    mockMvc
        .perform(
            post("/identities:resolve")
                .header("X-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void usersEndpointAllowsOwner() throws Exception {
    when(userService.getUser("user-1"))
        .thenReturn(new UserResponse("user-1", "n", "ja", "ACTIVE", List.of("USER")));

    mockMvc
        .perform(get("/users/user-1").with(user("user-1").roles("USER")))
        .andExpect(status().isOk());
  }

  @Test
  void usersEndpointAllowsInternalOwnerWithHeaders() throws Exception {
    when(userService.getUser("user-1"))
        .thenReturn(new UserResponse("user-1", "n", "ja", "ACTIVE", List.of("USER")));

    mockMvc
        .perform(
            get("/users/user-1")
                .header("X-Internal-Token", "test-internal-token")
                .header("X-User-Id", "user-1"))
        .andExpect(status().isOk());
  }

  @Test
  void usersEndpointRejectsInternalNonOwnerWithHeaders() throws Exception {
    mockMvc
        .perform(
            get("/users/user-2")
                .header("X-Internal-Token", "test-internal-token")
                .header("X-User-Id", "user-1"))
        .andExpect(status().isForbidden());
  }

  @Test
  void usersEndpointRejectsWhenInternalUserIdHeaderMissing() throws Exception {
    mockMvc
        .perform(get("/users/user-1").header("X-Internal-Token", "test-internal-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void usersEndpointRejectsWhenInternalTokenIsInvalid() throws Exception {
    mockMvc
        .perform(
            get("/users/user-1")
                .header("X-Internal-Token", "wrong-token")
                .header("X-User-Id", "user-1"))
        .andExpect(status().isForbidden());
  }

  @Test
  void usersEndpointRejectsNonOwner() throws Exception {
    mockMvc
        .perform(get("/users/user-2").with(user("user-1").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void usersEndpointAllowsAdminEvenIfDifferentUser() throws Exception {
    when(userService.getUser("user-2"))
        .thenReturn(new UserResponse("user-2", "n", "ja", "ACTIVE", List.of("USER")));

    mockMvc
        .perform(get("/users/user-2").with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void patchUsersEndpointRejectsNonOwner() throws Exception {
    final String body = objectMapper.writeValueAsString(new UserPatchRequest("n2", "en"));

    mockMvc
        .perform(
            patch("/users/user-2")
                .with(user("user-1").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void patchUsersEndpointAllowsInternalOwnerWithHeaders() throws Exception {
    final String body = objectMapper.writeValueAsString(new UserPatchRequest("n2", "en"));
    when(userService.patchUser(any(), any(UserPatchRequest.class)))
        .thenReturn(new UserResponse("user-1", "n2", "en", "ACTIVE", List.of("USER")));

    mockMvc
        .perform(
            patch("/users/user-1")
                .header("X-Internal-Token", "test-internal-token")
                .header("X-User-Id", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void adminEndpointRequiresAdminRole() throws Exception {
    mockMvc
        .perform(
            post("/admin/users/user-1:suspend")
                .with(user("user-1").roles("USER"))
                .header("X-Actor-User-Id", "user-1"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminEndpointAllowsAdminRole() throws Exception {
    doNothing().when(adminUserService).suspendUser(any(), any(), any());

    mockMvc
        .perform(
            post("/admin/users/user-1:suspend")
                .with(user("admin").roles("ADMIN"))
                .header("X-Actor-User-Id", "admin")
                .param("reason", "test"))
        .andExpect(status().isNoContent());
  }
}
