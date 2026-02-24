package com.example.matchmaking.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.matchmaking.api.request.JoinMatchmakingTicketRequest;
import com.example.matchmaking.api.response.CancelMatchmakingTicketResponse;
import com.example.matchmaking.api.response.JoinMatchmakingTicketResponse;
import com.example.matchmaking.api.response.MatchedTicketPayload;
import com.example.matchmaking.api.response.TicketStatusResponse;
import com.example.matchmaking.service.MatchmakingService;
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
@Import(ApiExceptionHandler.class)
class MatchmakingControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MatchmakingService matchmakingService;

  @Test
  void joinTicketReturns200() throws Exception {
    when(matchmakingService.join(any(), any(), any(JoinMatchmakingTicketRequest.class)))
        .thenReturn(
            new JoinMatchmakingTicketResponse("ticket-1", "QUEUED", "2026-02-24T12:01:00Z"));

    mockMvc
        .perform(
            post("/v1/matchmaking/queues/casual/tickets")
                .header("X-User-Id", "user-1")
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
  void getTicketStatusReturnsMatchedPayload() throws Exception {
    when(matchmakingService.getTicketStatus("ticket-1", "user-1"))
        .thenReturn(
            new TicketStatusResponse(
                "ticket-1",
                "MATCHED",
                "2026-02-24T12:01:00Z",
                new MatchedTicketPayload("match-1", java.util.List.of(), java.util.Map.of())));

    mockMvc
        .perform(get("/v1/matchmaking/tickets/ticket-1").header("X-User-Id", "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MATCHED"))
        .andExpect(jsonPath("$.matched.match_id").value("match-1"));
  }

  @Test
  void cancelTicketReturns200() throws Exception {
    when(matchmakingService.cancelTicket("ticket-1", "user-1"))
        .thenReturn(new CancelMatchmakingTicketResponse("ticket-1", "CANCELLED"));

    mockMvc
        .perform(delete("/v1/matchmaking/tickets/ticket-1").header("X-User-Id", "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void joinTicketReturns400WhenValidationFails() throws Exception {
    mockMvc
        .perform(
            post("/v1/matchmaking/queues/casual/tickets")
                .header("X-User-Id", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"attributes":{},"idempotency_key":"idem-1"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MATCHMAKING_VALIDATION_ERROR"));
  }
}
