/*
 * どこで: Matchmaking API
 * 何を: Join/Status/Cancel エンドポイントを公開する
 * なぜ: クライアントからのマッチメイク要求を受け付ける入口を提供するため
 */
package com.example.matchmaking.api;

import com.example.matchmaking.api.request.JoinMatchmakingTicketRequest;
import com.example.matchmaking.api.response.CancelMatchmakingTicketResponse;
import com.example.matchmaking.api.response.JoinMatchmakingTicketResponse;
import com.example.matchmaking.api.response.TicketStatusResponse;
import com.example.matchmaking.service.MatchmakingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/matchmaking")
@RequiredArgsConstructor
public class MatchmakingController {

  private static final String HEADER_USER_ID = "X-User-Id";
  private final MatchmakingService matchmakingService;

  @PostMapping("/queues/{mode}/tickets")
  public ResponseEntity<JoinMatchmakingTicketResponse> joinTicket(
      @PathVariable("mode") String mode,
      @RequestHeader(HEADER_USER_ID) String userId,
      @Valid @RequestBody JoinMatchmakingTicketRequest request) {
    return ResponseEntity.ok(matchmakingService.join(mode, userId, request));
  }

  @GetMapping("/tickets/{ticketId}")
  public ResponseEntity<TicketStatusResponse> getTicketStatus(
      @PathVariable("ticketId") String ticketId, @RequestHeader(HEADER_USER_ID) String userId) {
    return ResponseEntity.ok(matchmakingService.getTicketStatus(ticketId, userId));
  }

  @DeleteMapping("/tickets/{ticketId}")
  public ResponseEntity<CancelMatchmakingTicketResponse> cancelTicket(
      @PathVariable("ticketId") String ticketId, @RequestHeader(HEADER_USER_ID) String userId) {
    return ResponseEntity.ok(matchmakingService.cancelTicket(ticketId, userId));
  }
}
