/*
 * どこで: Gateway-BFF サービス層
 * 何を: matchmaking サービス呼び出しを担当する
 * なぜ: API 層から下流通信ロジックを分離するため
 */
package com.example.gateway_bff.service;

import com.example.gateway_bff.config.MatchmakingClientProperties;
import com.example.gateway_bff.service.dto.MatchmakingCancelTicketResponse;
import com.example.gateway_bff.service.dto.MatchmakingJoinTicketRequest;
import com.example.gateway_bff.service.dto.MatchmakingJoinTicketResponse;
import com.example.gateway_bff.service.dto.MatchmakingTicketStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MatchmakingClient {

  private final RestClient matchmakingRestClient;
  private final MatchmakingClientProperties properties;

  public MatchmakingClient(RestClient matchmakingRestClient, MatchmakingClientProperties properties) {
    this.matchmakingRestClient = matchmakingRestClient;
    this.properties = properties;
  }

  /**
   * 役割: matchmaking Join API を呼び出す。
   * 動作: userId ヘッダーを付与してチケット作成要求を下流へ転送し、応答を返す。
   * 前提: userId は認証済みユーザー ID、mode は `casual|rank`。
   */
  public MatchmakingJoinTicketResponse joinTicket(
      String mode, String userId, MatchmakingJoinTicketRequest request) {
    throw new UnsupportedOperationException("TODO: implement joinTicket");
  }

  /**
   * 役割: ticket 状態を下流から取得する。
   * 動作: userId ヘッダー付きで status API を呼び出し、応答を返す。
   * 前提: ticketId は空でない。
   */
  public MatchmakingTicketStatusResponse getTicketStatus(String ticketId, String userId) {
    throw new UnsupportedOperationException("TODO: implement getTicketStatus");
  }

  /**
   * 役割: ticket キャンセルを下流へ依頼する。
   * 動作: userId ヘッダー付きで cancel API を呼び出し、冪等応答を返す。
   * 前提: ticketId は空でない。
   */
  public MatchmakingCancelTicketResponse cancelTicket(String ticketId, String userId) {
    throw new UnsupportedOperationException("TODO: implement cancelTicket");
  }
}
