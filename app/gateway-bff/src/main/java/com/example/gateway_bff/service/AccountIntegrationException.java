package com.example.gateway_bff.service;

public class AccountIntegrationException extends RuntimeException {

  public enum Reason {
    UNAUTHORIZED,
    FORBIDDEN,
    BAD_GATEWAY,
    TIMEOUT,
    INVALID_RESPONSE
  }

  private final Reason reason;

  public AccountIntegrationException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public AccountIntegrationException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
