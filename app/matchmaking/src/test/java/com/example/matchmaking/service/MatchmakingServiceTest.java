package com.example.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.matchmaking.api.InvalidMatchmakingRequestException;
import com.example.matchmaking.api.TicketAccessDeniedException;
import com.example.matchmaking.api.TicketNotFoundException;
import com.example.matchmaking.api.request.JoinMatchmakingTicketRequest;
import com.example.matchmaking.config.MatchmakingProperties;
import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.TicketRecord;
import com.example.matchmaking.model.TicketStatus;
import com.example.matchmaking.repository.MatchmakingTicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MatchmakingServiceTest {

  private MatchmakingTicketRepository repository;
  private MatchmakingService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(MatchmakingTicketRepository.class);
    final MatchmakingProperties properties =
        new MatchmakingProperties(
            Duration.ofSeconds(60), Duration.ofSeconds(60), Duration.ofSeconds(1), 50, true);
    service = new MatchmakingService(repository, properties, new ObjectMapper());
  }

  @Test
  void joinThrowsWhenPartySizeIsNotOne() {
    final JoinMatchmakingTicketRequest request =
        new JoinMatchmakingTicketRequest(2, Map.of(), "idem-1");

    assertThatThrownBy(() -> service.join("casual", "user-1", request))
        .isInstanceOf(InvalidMatchmakingRequestException.class)
        .hasMessageContaining("party_size must be 1");
  }

  @Test
  void joinThrowsWhenIdempotencyKeyMissing() {
    final JoinMatchmakingTicketRequest request = new JoinMatchmakingTicketRequest(1, Map.of(), " ");

    assertThatThrownBy(() -> service.join("casual", "user-1", request))
        .isInstanceOf(InvalidMatchmakingRequestException.class)
        .hasMessageContaining("idempotency_key is required");
  }

  @Test
  void joinThrowsWhenModeUnsupported() {
    final JoinMatchmakingTicketRequest request =
        new JoinMatchmakingTicketRequest(1, Map.of(), "idem-1");

    assertThatThrownBy(() -> service.join("unknown", "user-1", request))
        .isInstanceOf(InvalidMatchmakingRequestException.class)
        .hasMessageContaining("unsupported mode");
  }

  @Test
  void joinReturnsQueuedTicket() {
    final Instant now = Instant.parse("2026-02-24T12:00:00Z");
    final Instant expiresAt = now.plusSeconds(60);
    when(repository.createOrReuseTicket(
            eq(MatchMode.CASUAL),
            eq("user-1"),
            eq("idem-1"),
            any(),
            eq(Duration.ofSeconds(60)),
            eq(Duration.ofSeconds(60))))
        .thenReturn(
            new TicketRecord(
                "ticket-1",
                "user-1",
                MatchMode.CASUAL,
                TicketStatus.QUEUED,
                now,
                expiresAt,
                "{}",
                null));

    final var response =
        service.join(
            "casual", "user-1", new JoinMatchmakingTicketRequest(1, Map.of("skill", 10), "idem-1"));

    assertThat(response.ticketId()).isEqualTo("ticket-1");
    assertThat(response.status()).isEqualTo("QUEUED");
    assertThat(response.expiresAt()).isEqualTo("2026-02-24T12:01:00Z");
  }

  @Test
  void getTicketStatusThrowsWhenNotFound() {
    when(repository.findTicketById("ticket-404")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getTicketStatus("ticket-404", "user-1"))
        .isInstanceOf(TicketNotFoundException.class);
  }

  @Test
  void getTicketStatusThrowsWhenOwnerMismatch() {
    when(repository.findTicketById("ticket-1"))
        .thenReturn(
            Optional.of(
                new TicketRecord(
                    "ticket-1",
                    "user-2",
                    MatchMode.CASUAL,
                    TicketStatus.QUEUED,
                    Instant.now(),
                    Instant.now().plusSeconds(60),
                    "{}",
                    null)));

    assertThatThrownBy(() -> service.getTicketStatus("ticket-1", "user-1"))
        .isInstanceOf(TicketAccessDeniedException.class);
  }

  @Test
  void getTicketStatusReturnsMatchedPayloadWhenMatched() {
    when(repository.findTicketById("ticket-1"))
        .thenReturn(
            Optional.of(
                new TicketRecord(
                    "ticket-1",
                    "user-1",
                    MatchMode.CASUAL,
                    TicketStatus.MATCHED,
                    Instant.parse("2026-02-24T12:00:00Z"),
                    Instant.parse("2026-02-24T12:01:00Z"),
                    "{}",
                    "match-1")));

    final var response = service.getTicketStatus("ticket-1", "user-1");

    assertThat(response.status()).isEqualTo("MATCHED");
    assertThat(response.matched()).isNotNull();
    assertThat(response.matched().matchId()).isEqualTo("match-1");
    assertThat(response.matched().peerUserIds()).isEmpty();
  }

  @Test
  void cancelTicketThrowsWhenOwnerMismatch() {
    when(repository.findTicketById("ticket-1"))
        .thenReturn(
            Optional.of(
                new TicketRecord(
                    "ticket-1",
                    "user-2",
                    MatchMode.CASUAL,
                    TicketStatus.QUEUED,
                    Instant.now(),
                    Instant.now().plusSeconds(60),
                    "{}",
                    null)));

    assertThatThrownBy(() -> service.cancelTicket("ticket-1", "user-1"))
        .isInstanceOf(TicketAccessDeniedException.class);
    verify(repository, never()).cancelTicket("ticket-1");
  }

  @Test
  void cancelTicketReturnsCancelledResponse() {
    when(repository.findTicketById("ticket-1"))
        .thenReturn(
            Optional.of(
                new TicketRecord(
                    "ticket-1",
                    "user-1",
                    MatchMode.CASUAL,
                    TicketStatus.QUEUED,
                    Instant.parse("2026-02-24T12:00:00Z"),
                    Instant.parse("2026-02-24T12:01:00Z"),
                    "{}",
                    null)));
    when(repository.cancelTicket("ticket-1"))
        .thenReturn(
            Optional.of(
                new TicketRecord(
                    "ticket-1",
                    "user-1",
                    MatchMode.CASUAL,
                    TicketStatus.CANCELLED,
                    Instant.parse("2026-02-24T12:00:00Z"),
                    Instant.parse("2026-02-24T12:01:00Z"),
                    "{}",
                    null)));

    final var response = service.cancelTicket("ticket-1", "user-1");

    assertThat(response.ticketId()).isEqualTo("ticket-1");
    assertThat(response.status()).isEqualTo("CANCELLED");
  }
}
