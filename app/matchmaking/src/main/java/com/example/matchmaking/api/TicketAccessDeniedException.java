package com.example.matchmaking.api;

public class TicketAccessDeniedException extends RuntimeException {
  public TicketAccessDeniedException(String ticketId) {
    super("ticket access denied: " + ticketId);
  }
}
