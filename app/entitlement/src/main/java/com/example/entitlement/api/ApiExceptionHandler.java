/*
 * どこで: Entitlement API
 * 何を: 例外を HTTP レスポンスへ変換する
 * なぜ: API 仕様に沿ったエラー応答を統一するため
 */
package com.example.entitlement.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
      IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiErrorResponse(ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT, ex.getMessage()));
  }

  @ExceptionHandler(InvalidEntitlementTransitionException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidEntitlementTransition(
      InvalidEntitlementTransitionException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiErrorResponse(ApiErrorCode.ENTITLEMENT_STATE_CONFLICT, ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    return badRequest(ex.getMessage());
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
    return badRequest(ex.getHeaderName() + " is required");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    // フィールド単位のメッセージを優先し、クライアントに最短で伝える。
    final String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .filter(this::hasText)
            .findFirst()
            .orElse("request body is invalid");
    return badRequest(message);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex) {
    // パス/ヘッダなどの検証エラーは最初の1件に絞って返す。
    final String message =
        ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .filter(this::hasText)
            .findFirst()
            .orElse("request is invalid");
    return badRequest(message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
    // JSONパーサの内部文言は露出せず、用途に合う短文へ正規化する。
    final String message = resolveUnreadableBodyMessage(ex);
    return badRequest(message);
  }

  private ResponseEntity<ApiErrorResponse> badRequest(String message) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiErrorResponse(ApiErrorCode.BAD_REQUEST, message));
  }

  private String resolveUnreadableBodyMessage(HttpMessageNotReadableException ex) {
    final String rawMessage = Optional.ofNullable(ex.getMessage()).orElse("");
    if (rawMessage.contains("Required request body is missing")) {
      return "request body is required";
    }
    return "request body is invalid";
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
