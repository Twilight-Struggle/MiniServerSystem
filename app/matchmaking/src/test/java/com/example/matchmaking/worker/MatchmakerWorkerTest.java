package com.example.matchmaking.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.matchmaking.config.MatchmakingProperties;
import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.MatchPair;
import com.example.matchmaking.repository.MatchmakingMatchRepository;
import com.example.matchmaking.repository.MatchmakingTicketRepository;
import com.example.matchmaking.service.MatchmakingEventPublisher;
import com.example.matchmaking.service.MatchmakingMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MatchmakerWorkerTest {

  @Test
  void runPublishesWhenMatchFound() {
    final MatchmakingMetrics metrics = Mockito.mock(MatchmakingMetrics.class);
    final MatchmakingTicketRepository ticketRepository =
        Mockito.mock(MatchmakingTicketRepository.class);
    final MatchmakingMatchRepository matchRepository =
        Mockito.mock(MatchmakingMatchRepository.class);
    final MatchmakingEventPublisher publisher = Mockito.mock(MatchmakingEventPublisher.class);
    final MatchmakingProperties properties =
        new MatchmakingProperties(
            Duration.ofSeconds(60), Duration.ofSeconds(60), Duration.ofSeconds(1), 2, true);

    when(ticketRepository.queueDepth(MatchMode.CASUAL)).thenReturn(2L);
    when(ticketRepository.queueDepth(MatchMode.RANK)).thenReturn(0L);
    when(ticketRepository.oldestQueueAgeSeconds(any())).thenReturn(Optional.of(3L));
    when(matchRepository.matchTwo(eq(MatchMode.CASUAL), any(Instant.class)))
        .thenReturn(
            Optional.of(
                new MatchPair("match-1", MatchMode.CASUAL, "ticket-1", "ticket-2", Instant.now())))
        .thenReturn(Optional.empty());

    final MatchmakerWorker worker =
        new MatchmakerWorker(metrics, properties, ticketRepository, matchRepository, publisher);

    worker.run();

    verify(publisher).publishMatched(any(MatchPair.class));
    verify(metrics).recordMatchResult("matched");
  }

  @Test
  void runSkipsMatchWhenDepthLessThanTwo() {
    final MatchmakingMetrics metrics = Mockito.mock(MatchmakingMetrics.class);
    final MatchmakingTicketRepository ticketRepository =
        Mockito.mock(MatchmakingTicketRepository.class);
    final MatchmakingMatchRepository matchRepository =
        Mockito.mock(MatchmakingMatchRepository.class);
    final MatchmakingEventPublisher publisher = Mockito.mock(MatchmakingEventPublisher.class);
    final MatchmakingProperties properties =
        new MatchmakingProperties(
            Duration.ofSeconds(60), Duration.ofSeconds(60), Duration.ofSeconds(1), 2, true);

    when(ticketRepository.queueDepth(any())).thenReturn(1L);
    when(ticketRepository.oldestQueueAgeSeconds(any())).thenReturn(Optional.of(1L));

    final MatchmakerWorker worker =
        new MatchmakerWorker(metrics, properties, ticketRepository, matchRepository, publisher);

    worker.run();

    verify(matchRepository, never()).matchTwo(any(), any());
    verify(publisher, never()).publishMatched(any());
  }
}
