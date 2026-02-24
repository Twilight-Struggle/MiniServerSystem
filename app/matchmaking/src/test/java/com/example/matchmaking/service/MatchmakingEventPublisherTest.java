package com.example.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.matchmaking.config.MatchmakingNatsProperties;
import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.MatchPair;
import io.nats.client.JetStream;
import io.nats.client.impl.Headers;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MatchmakingEventPublisherTest {

  @Test
  void publishesMatchedEvent() throws Exception {
    final JetStream jetStream = Mockito.mock(JetStream.class);
    final MatchmakingNatsProperties properties =
        new MatchmakingNatsProperties(
            "matchmaking.events", "matchmaking-events", java.time.Duration.ofMinutes(2));
    final Clock clock = Clock.fixed(Instant.parse("2026-02-24T12:00:00Z"), ZoneOffset.UTC);
    final MatchmakingEventPublisher publisher =
        new MatchmakingEventPublisher(jetStream, properties, clock);

    final MatchPair pair =
        new MatchPair("match-1", MatchMode.CASUAL, "ticket-1", "ticket-2", Instant.now(clock));
    publisher.publishMatched(pair);

    verify(jetStream).publish(eq("matchmaking.events"), any(Headers.class), any(byte[].class));
  }

  @Test
  void throwsWhenMatchIdMissing() {
    final JetStream jetStream = Mockito.mock(JetStream.class);
    final MatchmakingNatsProperties properties =
        new MatchmakingNatsProperties(
            "matchmaking.events", "matchmaking-events", java.time.Duration.ofMinutes(2));
    final Clock clock = Clock.systemUTC();
    final MatchmakingEventPublisher publisher =
        new MatchmakingEventPublisher(jetStream, properties, clock);

    assertThatThrownBy(
            () ->
                publisher.publishMatched(
                    new MatchPair(" ", MatchMode.CASUAL, "ticket-1", "ticket-2", Instant.now())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void wrapsIOException() throws Exception {
    final JetStream jetStream = Mockito.mock(JetStream.class);
    when(jetStream.publish(any(String.class), any(Headers.class), any(byte[].class)))
        .thenThrow(new IOException("boom"));
    final MatchmakingNatsProperties properties =
        new MatchmakingNatsProperties(
            "matchmaking.events", "matchmaking-events", java.time.Duration.ofMinutes(2));
    final Clock clock = Clock.systemUTC();
    final MatchmakingEventPublisher publisher =
        new MatchmakingEventPublisher(jetStream, properties, clock);

    assertThatThrownBy(
            () ->
                publisher.publishMatched(
                    new MatchPair(
                        UUID.randomUUID().toString(),
                        MatchMode.CASUAL,
                        "ticket-1",
                        "ticket-2",
                        Instant.now())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("failed to publish");
  }

  @Test
  void noopPublisherDoesNothing() {
    final NoopMatchmakingEventPublisher publisher = new NoopMatchmakingEventPublisher();

    publisher.publishMatched(
        new MatchPair("match-1", MatchMode.CASUAL, "ticket-1", "ticket-2", Instant.now()));
  }
}
