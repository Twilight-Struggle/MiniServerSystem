/*
 * どこで: Gateway-BFF サービス層
 * 何を: entitlement 下流呼び出し失敗を表現する
 * なぜ: API 層で HTTP ステータスへ一貫変換するため
 */
package com.example.gateway_bff.service;

public class EntitlementIntegrationException extends RuntimeException {

  public enum Reason {
    NOT_FOUND,
    TIMEOUT,
    INVALID_RESPONSE,
    BAD_GATEWAY
  }

  private final Reason reason;

  public EntitlementIntegrationException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public EntitlementIntegrationException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
