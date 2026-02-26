package com.example.gateway_bff.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gateway_bff.api.response.ProfileAggregateResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.GatewayMetrics;
import com.example.gateway_bff.service.OidcAuthenticatedUserService;
import com.example.gateway_bff.service.ProfileAccessDeniedException;
import com.example.gateway_bff.service.ProfileAggregateService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileAggregateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GatewayApiExceptionHandler.class)
class ProfileAggregateControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OidcAuthenticatedUserService oidcAuthenticatedUserService;
  @MockitoBean private ProfileAggregateService profileAggregateService;
  @MockitoBean private GatewayMetrics gatewayMetrics;

  @Test
  void getProfileReturnsAggregatedPayload() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(profileAggregateService.aggregateByUserId(
            "user-1", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), "ticket-1"))
        .thenReturn(new ProfileAggregateResponse(Map.of("user_id", "user-1"), Map.of(), Map.of()));

    mockMvc
        .perform(get("/v1/users/user-1/profile").queryParam("ticketId", "ticket-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.user_id").value("user-1"));
  }

  @Test
  void getProfileReturns403WhenDifferentUser() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(profileAggregateService.aggregateByUserId(
            "user-2", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), null))
        .thenThrow(new ProfileAccessDeniedException("profile access denied"));

    mockMvc
        .perform(get("/v1/users/user-2/profile"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PROFILE_FORBIDDEN"));
  }
}
