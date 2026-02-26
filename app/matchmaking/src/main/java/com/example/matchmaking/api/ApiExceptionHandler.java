package com.example.matchmaking.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(InvalidMatchmakingRequestException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
      InvalidMatchmakingRequestException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiErrorResponse("MATCHMAKING_BAD_REQUEST", ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiErrorResponse("MATCHMAKING_VALIDATION_ERROR", "request validation failed"));
  }

  @ExceptionHandler(TicketNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNotFound(TicketNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiErrorResponse("MATCHMAKING_TICKET_NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(TicketAccessDeniedException.class)
  public ResponseEntity<ApiErrorResponse> handleAccessDenied(TicketAccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiErrorResponse("MATCHMAKING_TICKET_FORBIDDEN", ex.getMessage()));
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiErrorResponse("MATCHMAKING_INTERNAL_ERROR", ex.getMessage()));
  }
}
