package com.example.gateway_bff.api;

import com.example.gateway_bff.service.AccountInactiveException;
import com.example.gateway_bff.service.AccountIntegrationException;
import com.example.gateway_bff.service.GatewayMetrics;
import com.example.gateway_bff.service.MatchmakingIntegrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class GatewayApiExceptionHandler {

  private final GatewayMetrics gatewayMetrics;

  @ExceptionHandler(AccountInactiveException.class)
  public ResponseEntity<ApiErrorResponse> handleAccountInactive(AccountInactiveException ex) {
    gatewayMetrics.recordAccountIntegrationError("ACCOUNT_INACTIVE");
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiErrorResponse("ACCOUNT_INACTIVE", ex.getMessage()));
  }

  @ExceptionHandler(AccountIntegrationException.class)
  public ResponseEntity<ApiErrorResponse> handleAccountIntegration(AccountIntegrationException ex) {
    final String code =
        switch (ex.reason()) {
          case UNAUTHORIZED -> "ACCOUNT_UNAUTHORIZED";
          case FORBIDDEN -> "ACCOUNT_FORBIDDEN";
          case NOT_FOUND -> "ACCOUNT_NOT_FOUND";
          case TIMEOUT -> "ACCOUNT_TIMEOUT";
          case INVALID_RESPONSE -> "ACCOUNT_INVALID_RESPONSE";
          case BAD_GATEWAY -> "ACCOUNT_BAD_GATEWAY";
        };
    final HttpStatus status =
        switch (ex.reason()) {
          case UNAUTHORIZED, INVALID_RESPONSE, BAD_GATEWAY -> HttpStatus.BAD_GATEWAY;
          case FORBIDDEN -> HttpStatus.FORBIDDEN;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
        };
    gatewayMetrics.recordAccountIntegrationError(code);
    return ResponseEntity.status(status).body(new ApiErrorResponse(code, ex.getMessage()));
  }

  @ExceptionHandler(MatchmakingIntegrationException.class)
  public ResponseEntity<ApiErrorResponse> handleMatchmakingIntegration(
      MatchmakingIntegrationException ex) {
    final String code =
        switch (ex.reason()) {
          case FORBIDDEN -> "MATCHMAKING_FORBIDDEN";
          case NOT_FOUND -> "MATCHMAKING_NOT_FOUND";
          case TIMEOUT -> "MATCHMAKING_TIMEOUT";
          case INVALID_RESPONSE -> "MATCHMAKING_INVALID_RESPONSE";
          case BAD_GATEWAY -> "MATCHMAKING_BAD_GATEWAY";
        };
    final HttpStatus status =
        switch (ex.reason()) {
          case FORBIDDEN -> HttpStatus.FORBIDDEN;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
          case INVALID_RESPONSE, BAD_GATEWAY -> HttpStatus.BAD_GATEWAY;
        };
    gatewayMetrics.recordAccountIntegrationError(code);
    return ResponseEntity.status(status).body(new ApiErrorResponse(code, ex.getMessage()));
  }
}
