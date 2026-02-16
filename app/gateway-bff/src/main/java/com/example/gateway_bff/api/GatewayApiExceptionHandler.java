package com.example.gateway_bff.api;

import com.example.gateway_bff.service.AccountInactiveException;
import com.example.gateway_bff.service.AccountIntegrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GatewayApiExceptionHandler {

  @ExceptionHandler(AccountInactiveException.class)
  public ResponseEntity<ApiErrorResponse> handleAccountInactive(AccountInactiveException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiErrorResponse("ACCOUNT_INACTIVE", ex.getMessage()));
  }

  @ExceptionHandler(AccountIntegrationException.class)
  public ResponseEntity<ApiErrorResponse> handleAccountIntegration(AccountIntegrationException ex) {
    return switch (ex.reason()) {
      case UNAUTHORIZED ->
          ResponseEntity.status(HttpStatus.BAD_GATEWAY)
              .body(new ApiErrorResponse("ACCOUNT_UNAUTHORIZED", ex.getMessage()));
      case FORBIDDEN ->
          ResponseEntity.status(HttpStatus.BAD_GATEWAY)
              .body(new ApiErrorResponse("ACCOUNT_FORBIDDEN", ex.getMessage()));
      case NOT_FOUND ->
          ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(new ApiErrorResponse("ACCOUNT_NOT_FOUND", ex.getMessage()));
      case TIMEOUT ->
          ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
              .body(new ApiErrorResponse("ACCOUNT_TIMEOUT", ex.getMessage()));
      case INVALID_RESPONSE ->
          ResponseEntity.status(HttpStatus.BAD_GATEWAY)
              .body(new ApiErrorResponse("ACCOUNT_INVALID_RESPONSE", ex.getMessage()));
      case BAD_GATEWAY ->
          ResponseEntity.status(HttpStatus.BAD_GATEWAY)
              .body(new ApiErrorResponse("ACCOUNT_BAD_GATEWAY", ex.getMessage()));
    };
  }
}
