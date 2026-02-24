/*
 * どこで: Gateway-BFF API
 * 何を: クライアント向け matchmaking API を公開する
 * なぜ: 直接 matchmaking を公開せず BFF 経由の境界を維持するため
 */
package com.example.gateway_bff.api;

import com.example.gateway_bff.api.request.MatchmakingJoinRequest;
import com.example.gateway_bff.api.response.MatchmakingCancelResponse;
import com.example.gateway_bff.api.response.MatchmakingJoinResponse;
import com.example.gateway_bff.api.response.MatchmakingMatchedPayloadResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.MatchmakingClient;
import com.example.gateway_bff.service.OidcAuthenticatedUserService;
import com.example.gateway_bff.service.dto.MatchmakingJoinTicketRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/matchmaking")
@RequiredArgsConstructor
public class MatchmakingController {

  private final OidcAuthenticatedUserService oidcAuthenticatedUserService;
  private final MatchmakingClient matchmakingClient;

  @PostMapping("/queues/{mode}/tickets")
  public ResponseEntity<MatchmakingJoinResponse> joinTicket(
      @PathVariable("mode") String mode,
      @RequestBody MatchmakingJoinRequest request,
      Authentication authentication) {
    final AuthenticatedUser requester =
        oidcAuthenticatedUserService.resolveAuthenticatedUser(authentication);
    final var response =
        matchmakingClient.joinTicket(
            mode,
            requester.userId(),
            new MatchmakingJoinTicketRequest(
                request.partySize(), request.attributes(), request.idempotencyKey()));
    return ResponseEntity.ok(
        new MatchmakingJoinResponse(response.ticketId(), response.status(), response.expiresAt()));
  }

  @GetMapping("/tickets/{ticketId}")
  public ResponseEntity<com.example.gateway_bff.api.response.MatchmakingTicketStatusResponse>
      getTicketStatus(@PathVariable("ticketId") String ticketId, Authentication authentication) {
    final AuthenticatedUser requester =
        oidcAuthenticatedUserService.resolveAuthenticatedUser(authentication);
    final var response = matchmakingClient.getTicketStatus(ticketId, requester.userId());
    final MatchmakingMatchedPayloadResponse matched =
        response.matched() == null
            ? null
            : new MatchmakingMatchedPayloadResponse(
                response.matched().matchId(),
                response.matched().peerUserIds(),
                response.matched().session());
    return ResponseEntity.ok(
        new com.example.gateway_bff.api.response.MatchmakingTicketStatusResponse(
            response.ticketId(), response.status(), response.expiresAt(), matched));
  }

  @DeleteMapping("/tickets/{ticketId}")
  public ResponseEntity<MatchmakingCancelResponse> cancelTicket(
      @PathVariable("ticketId") String ticketId, Authentication authentication) {
    final AuthenticatedUser requester =
        oidcAuthenticatedUserService.resolveAuthenticatedUser(authentication);
    final var response = matchmakingClient.cancelTicket(ticketId, requester.userId());
    return ResponseEntity.ok(new MatchmakingCancelResponse(response.ticketId(), response.status()));
  }
}
