package com.example.matchmaking.worker;

import com.example.matchmaking.config.MatchmakingProperties;
import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.MatchPair;
import com.example.matchmaking.repository.MatchmakingMatchRepository;
import com.example.matchmaking.repository.MatchmakingTicketRepository;
import com.example.matchmaking.service.MatchmakingEventPublisher;
import com.example.matchmaking.service.MatchmakingMetrics;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "matchmaking.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MatchmakerWorker {

  private static final Logger logger = LoggerFactory.getLogger(MatchmakerWorker.class);

  private final MatchmakingMetrics metrics;
  private final MatchmakingProperties properties;
  private final MatchmakingTicketRepository ticketRepository;
  private final MatchmakingMatchRepository matchRepository;
  private final MatchmakingEventPublisher eventPublisher;

  public MatchmakerWorker(
      MatchmakingMetrics metrics,
      MatchmakingProperties properties,
      MatchmakingTicketRepository ticketRepository,
      MatchmakingMatchRepository matchRepository,
      MatchmakingEventPublisher eventPublisher) {
    this.metrics = metrics;
    this.properties = properties;
    this.ticketRepository = ticketRepository;
    this.matchRepository = matchRepository;
    this.eventPublisher = eventPublisher;
  }

  @Scheduled(fixedDelayString = "${matchmaking.worker-poll-interval}")
  public void run() {
    for (MatchMode mode : MatchMode.values()) {
      try {
        final long depth = ticketRepository.queueDepth(mode);
        metrics.updateQueueDepth(mode.value(), depth);
        metrics.updateOldestQueueAge(
            mode.value(), ticketRepository.oldestQueueAgeSeconds(mode).orElse(0L));
        if (depth < 2) {
          continue;
        }
        final int attempts = Math.max(1, properties.workerBatchSize() / 2);
        for (int i = 0; i < attempts; i++) {
          final MatchPair pair = matchRepository.matchTwo(mode, Instant.now()).orElse(null);
          if (pair == null) {
            break;
          }
          eventPublisher.publishMatched(pair);
          metrics.recordMatchResult("matched");
          recordTimeToMatch(pair);
        }
      } catch (RuntimeException ex) {
        logger.warn("matchmaker worker loop failed mode={}", mode.value(), ex);
        metrics.recordDependencyError("worker_loop");
      }
    }
  }

  private void recordTimeToMatch(MatchPair pair) {
    recordTicketTimeToMatch(pair.ticketId1(), pair.matchedAt());
    recordTicketTimeToMatch(pair.ticketId2(), pair.matchedAt());
  }

  private void recordTicketTimeToMatch(String ticketId, Instant matchedAt) {
    ticketRepository
        .findTicketById(ticketId)
        .ifPresentOrElse(
            ticket -> {
              if (ticket.createdAt() == null) {
                metrics.recordDependencyError("time_to_match_missing_created_at");
                logger.warn("ticket created_at missing ticketId={}", ticketId);
                return;
              }
              metrics.recordTimeToMatchSeconds(
                  Duration.between(ticket.createdAt(), matchedAt).toSeconds());
            },
            () -> {
              metrics.recordDependencyError("time_to_match_ticket_not_found");
              logger.warn("matched ticket not found for time-to-match ticketId={}", ticketId);
            });
  }
}
