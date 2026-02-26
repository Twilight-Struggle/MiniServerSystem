package com.example.matchmaking.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void handleInvalidRequestReturns400() {
    final var response =
        handler.handleInvalidRequest(new InvalidMatchmakingRequestException("bad"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .isEqualTo(new ApiErrorResponse("MATCHMAKING_BAD_REQUEST", "bad"));
  }

  @Test
  void handleValidationReturns400() {
    final var response = handler.handleValidation((MethodArgumentNotValidException) null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("MATCHMAKING_VALIDATION_ERROR");
  }

  @Test
  void handleNotFoundReturns404() {
    final var response = handler.handleNotFound(new TicketNotFoundException("t1"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("MATCHMAKING_TICKET_NOT_FOUND");
  }

  @Test
  void handleAccessDeniedReturns403() {
    final var response = handler.handleAccessDenied(new TicketAccessDeniedException("t1"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().code()).isEqualTo("MATCHMAKING_TICKET_FORBIDDEN");
  }

  @Test
  void handleRuntimeReturns500() {
    final var response = handler.handleRuntime(new RuntimeException("oops"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().code()).isEqualTo("MATCHMAKING_INTERNAL_ERROR");
  }
}
