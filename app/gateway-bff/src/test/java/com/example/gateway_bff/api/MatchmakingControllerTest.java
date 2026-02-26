package com.example.gateway_bff.api;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.GatewayMetrics;
import com.example.gateway_bff.service.MatchmakingClient;
import com.example.gateway_bff.service.MatchmakingIntegrationException;
import com.example.gateway_bff.service.OidcAuthenticatedUserService;
import com.example.gateway_bff.service.dto.MatchmakingCancelTicketResponse;
import com.example.gateway_bff.service.dto.MatchmakingJoinTicketResponse;
import com.example.gateway_bff.service.dto.MatchmakingMatchedPayload;
import com.example.gateway_bff.service.dto.MatchmakingTicketStatusResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchmakingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GatewayApiExceptionHandler.class)
class MatchmakingControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OidcAuthenticatedUserService oidcAuthenticatedUserService;
  @MockitoBean private MatchmakingClient matchmakingClient;
  @MockitoBean private GatewayMetrics gatewayMetrics;

  @Test
  void joinTicketReturns200() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(matchmakingClient.joinTicket(any(), any(), any()))
        .thenReturn(
            new MatchmakingJoinTicketResponse("ticket-1", "QUEUED", "2026-02-24T12:01:00Z"));

    mockMvc
        .perform(
            post("/v1/matchmaking/queues/casual/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"party_size":1,"attributes":{"skill":10},"idempotency_key":"idem-1"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ticket_id").value("ticket-1"))
        .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  @Test
  void getTicketStatusReturns200() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(matchmakingClient.getTicketStatus(any(), any()))
        .thenReturn(
            new MatchmakingTicketStatusResponse(
                "ticket-1",
                "MATCHED",
                "2026-02-24T12:01:00Z",
                new MatchmakingMatchedPayload("match-1", List.of(), Map.of())));

    mockMvc
        .perform(get("/v1/matchmaking/tickets/ticket-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MATCHED"))
        .andExpect(jsonPath("$.matched.match_id").value("match-1"));
  }

  @Test
  void cancelTicketReturns200() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(matchmakingClient.cancelTicket(any(), any()))
        .thenReturn(new MatchmakingCancelTicketResponse("ticket-1", "CANCELLED"));

    mockMvc
        .perform(delete("/v1/matchmaking/tickets/ticket-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void getTicketStatusReturns404WhenNotFound() throws Exception {
    when(oidcAuthenticatedUserService.resolveAuthenticatedUser(any()))
        .thenReturn(new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")));
    when(matchmakingClient.getTicketStatus(any(), any()))
        .thenThrow(
            new MatchmakingIntegrationException(
                MatchmakingIntegrationException.Reason.NOT_FOUND, "not found"));

    mockMvc
        .perform(get("/v1/matchmaking/tickets/ticket-404"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MATCHMAKING_NOT_FOUND"));
  }
}
