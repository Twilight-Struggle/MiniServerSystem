package com.example.gateway_bff.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gateway_bff.api.response.LoginRedirectResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.OidcCallbackService;
import com.example.gateway_bff.service.OidcLoginService;
import com.example.gateway_bff.service.SessionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OidcLoginService oidcLoginService;

  @MockitoBean private OidcCallbackService oidcCallbackService;

  @MockitoBean private SessionService sessionService;

  @Test
  void loginReturnsRedirectInfo() throws Exception {
    when(oidcLoginService.prepareLogin())
        .thenReturn(new LoginRedirectResponse("https://example.com", "st"));

    mockMvc
        .perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("st"));
  }

  @Test
  void meReturnsUnauthorizedWhenSessionMissing() throws Exception {
    mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void meReturnsUserWhenSessionExists() throws Exception {
    when(sessionService.findAuthenticatedUser(any()))
        .thenReturn(Optional.of(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER"))));

    mockMvc
        .perform(get("/me").cookie(new jakarta.servlet.http.Cookie("MSS_SESSION", "sid")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("user-1"));
  }

  @Test
  void callbackReturnsNoContent() throws Exception {
    when(oidcCallbackService.handleCallback("st", "cd"))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(sessionService.createSession(any())).thenReturn("sid");

    mockMvc
        .perform(get("/callback").param("state", "st").param("code", "cd"))
        .andExpect(status().isNoContent());
  }

  @Test
  void logoutReturnsNoContent() throws Exception {
    mockMvc
        .perform(post("/logout").cookie(new jakarta.servlet.http.Cookie("MSS_SESSION", "sid")))
        .andExpect(status().isNoContent());
  }
}
