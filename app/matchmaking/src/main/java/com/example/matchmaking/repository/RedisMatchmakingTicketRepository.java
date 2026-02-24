/*
 * どこで: Matchmaking Repository 層
 * 何を: MatchmakingTicketRepository の Redis 実装を提供する
 * なぜ: ticket/queue/idempotency の永続化を Redis に集約するため
 */
package com.example.matchmaking.repository;

import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.TicketRecord;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisMatchmakingTicketRepository implements MatchmakingTicketRepository {

  private final StringRedisTemplate redisTemplate;

  public RedisMatchmakingTicketRepository(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public TicketRecord createOrReuseTicket(
      MatchMode mode,
      String userId,
      String idempotencyKey,
      String attributesJson,
      Duration ticketTtl,
      Duration idempotencyTtl) {
    throw new UnsupportedOperationException("TODO: implement createOrReuseTicket");
  }

  @Override
  public Optional<TicketRecord> findTicketById(String ticketId) {
    throw new UnsupportedOperationException("TODO: implement findTicketById");
  }

  @Override
  public Optional<TicketRecord> cancelTicket(String ticketId) {
    throw new UnsupportedOperationException("TODO: implement cancelTicket");
  }

  @Override
  public long queueDepth(MatchMode mode) {
    throw new UnsupportedOperationException("TODO: implement queueDepth");
  }

  @Override
  public Optional<Long> oldestQueueAgeSeconds(MatchMode mode) {
    throw new UnsupportedOperationException("TODO: implement oldestQueueAgeSeconds");
  }
}
