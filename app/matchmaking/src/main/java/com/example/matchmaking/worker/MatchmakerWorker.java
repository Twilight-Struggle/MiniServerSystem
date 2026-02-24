package com.example.matchmaking.worker;

import com.example.matchmaking.config.MatchmakingProperties;
import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.MatchPair;
import com.example.matchmaking.repository.MatchmakingMatchRepository;
import com.example.matchmaking.repository.MatchmakingTicketRepository;
import com.example.matchmaking.service.MatchmakingEventPublisher;
import com.example.matchmaking.service.MatchmakingMetrics;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "matchmaking.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MatchmakerWorker {

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
        }
      } catch (RuntimeException ex) {
        metrics.recordDependencyError("worker_loop");
      }
    }
  }
}
