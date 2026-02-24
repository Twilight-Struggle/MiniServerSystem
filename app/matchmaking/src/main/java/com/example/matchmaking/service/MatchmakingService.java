/*
 * どこで: Matchmaking サービス層
 * 何を: Join/Status/Cancel のユースケースを実装する
 * なぜ: API 層からドメイン処理を分離し、TDD で段階実装するため
 */
package com.example.matchmaking.service;

import com.example.matchmaking.api.InvalidMatchmakingRequestException;
import com.example.matchmaking.api.TicketNotFoundException;
import com.example.matchmaking.api.request.JoinMatchmakingTicketRequest;
import com.example.matchmaking.api.response.CancelMatchmakingTicketResponse;
import com.example.matchmaking.api.response.JoinMatchmakingTicketResponse;
import com.example.matchmaking.api.response.TicketStatusResponse;
import com.example.matchmaking.config.MatchmakingProperties;
import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.repository.MatchmakingTicketRepository;
import org.springframework.stereotype.Service;

@Service
public class MatchmakingService {

  private final MatchmakingTicketRepository ticketRepository;
  private final MatchmakingProperties properties;

  public MatchmakingService(
      MatchmakingTicketRepository ticketRepository, MatchmakingProperties properties) {
    this.ticketRepository = ticketRepository;
    this.properties = properties;
  }

  /**
   * 役割: Join API を処理し ticket をキューへ追加する。
   * 動作: mode と party_size を検証し、idempotency を評価した上で ticket を返却する。
   * 前提: userId は認証済みの内部ユーザー ID が渡されること。
   */
  public JoinMatchmakingTicketResponse join(
      String mode, String userId, JoinMatchmakingTicketRequest request) {
    validateJoinRequest(mode, userId, request);
    throw new UnsupportedOperationException("TODO: implement join");
  }

  /**
   * 役割: ticket の現在状態を返す。
   * 動作: ticket 所有者チェックを行い、必要なら EXPIRED 判定を反映して返す。
   * 前提: userId は認証済みユーザー。
   */
  public TicketStatusResponse getTicketStatus(String ticketId, String userId) {
    validateTicketOwner(ticketId, userId);
    throw new UnsupportedOperationException("TODO: implement getTicketStatus");
  }

  /**
   * 役割: ticket のキャンセル要求を処理する。
   * 動作: 所有者チェック後、status=QUEUED の場合に CANCELLED へ遷移し、終端状態でも 200 応答を返す。
   * 前提: userId は認証済みユーザー。
   */
  public CancelMatchmakingTicketResponse cancelTicket(String ticketId, String userId) {
    validateTicketOwner(ticketId, userId);
    throw new UnsupportedOperationException("TODO: implement cancelTicket");
  }

  private void validateJoinRequest(
      String mode, String userId, JoinMatchmakingTicketRequest request) {
    if (mode == null || mode.isBlank()) {
      throw new InvalidMatchmakingRequestException("mode is required");
    }
    MatchMode.fromValue(mode);
    if (userId == null || userId.isBlank()) {
      throw new InvalidMatchmakingRequestException("userId is required");
    }
    if (request == null) {
      throw new InvalidMatchmakingRequestException("request is required");
    }
    if (request.partySize() == null || request.partySize() != 1) {
      throw new InvalidMatchmakingRequestException("party_size must be 1");
    }
  }

  private void validateTicketOwner(String ticketId, String userId) {
    if (ticketId == null || ticketId.isBlank()) {
      throw new InvalidMatchmakingRequestException("ticketId is required");
    }
    if (userId == null || userId.isBlank()) {
      throw new InvalidMatchmakingRequestException("userId is required");
    }
    // TODO: ticket を読み出して owner 判定し、合致しなければ 404/403 方針に沿って例外化する。
    throw new TicketNotFoundException(ticketId);
  }
}
