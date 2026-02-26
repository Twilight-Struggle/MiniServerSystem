/*
 * どこで: Matchmaking API
 * 何を: ticket 未検出を表現する
 * なぜ: status/cancel の 404 応答へ変換するため
 */
package com.example.matchmaking.api;

public class TicketNotFoundException extends RuntimeException {
  public TicketNotFoundException(String ticketId) {
    super("ticket not found: " + ticketId);
  }
}
