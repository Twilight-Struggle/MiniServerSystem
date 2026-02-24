package com.example.matchmaking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.TicketStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

class RedisRepositorySkeletonTest {

  @SuppressWarnings("unchecked")
  @Test
  void createOrReuseCreatesNewTicketWhenIdempotencyMissing() {
    final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    final ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
    final HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
    final ZSetOperations<String, String> zSetOps = Mockito.mock(ZSetOperations.class);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(redisTemplate.opsForHash()).thenReturn(hashOps);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    when(valueOps.get("mm:idemp:user-1:casual:idem-1")).thenReturn(null);

    final RedisMatchmakingTicketRepository repository =
        new RedisMatchmakingTicketRepository(redisTemplate);

    final var ticket =
        repository.createOrReuseTicket(
            MatchMode.CASUAL,
            "user-1",
            "idem-1",
            "{}",
            Duration.ofSeconds(60),
            Duration.ofSeconds(60));

    assertThat(ticket.userId()).isEqualTo("user-1");
    assertThat(ticket.mode()).isEqualTo(MatchMode.CASUAL);
    assertThat(ticket.status()).isEqualTo(TicketStatus.QUEUED);
    verify(hashOps).putAll(eq("mm:ticket:" + ticket.ticketId()), any(Map.class));
    verify(zSetOps).add(eq("mm:queue:casual"), eq(ticket.ticketId()), any(Double.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  void createOrReuseReturnsExistingTicketWhenIdempotencyHit() {
    final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    final ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
    final HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(redisTemplate.opsForHash()).thenReturn(hashOps);
    when(valueOps.get("mm:idemp:user-1:casual:idem-1")).thenReturn("ticket-1");
    when(hashOps.entries("mm:ticket:ticket-1"))
        .thenReturn(
            Map.of(
                "user_id", "user-1",
                "mode", "casual",
                "status", "QUEUED",
                "created_at", "2026-02-24T12:00:00Z",
                "expires_at", "2026-02-24T12:01:00Z",
                "attributes", "{}"));

    final RedisMatchmakingTicketRepository repository =
        new RedisMatchmakingTicketRepository(redisTemplate);

    final var ticket =
        repository.createOrReuseTicket(
            MatchMode.CASUAL,
            "user-1",
            "idem-1",
            "{}",
            Duration.ofSeconds(60),
            Duration.ofSeconds(60));

    assertThat(ticket.ticketId()).isEqualTo("ticket-1");
  }

  @SuppressWarnings("unchecked")
  @Test
  void cancelTicketUpdatesQueuedStatus() {
    final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    final HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
    final ZSetOperations<String, String> zSetOps = Mockito.mock(ZSetOperations.class);
    when(redisTemplate.opsForHash()).thenReturn(hashOps);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    when(hashOps.entries("mm:ticket:ticket-1"))
        .thenReturn(
            Map.of(
                "user_id", "user-1",
                "mode", "casual",
                "status", "QUEUED",
                "created_at", "2026-02-24T12:00:00Z",
                "expires_at", Instant.now().plusSeconds(60).toString(),
                "attributes", "{}"));

    final RedisMatchmakingTicketRepository repository =
        new RedisMatchmakingTicketRepository(redisTemplate);

    final var cancelled = repository.cancelTicket("ticket-1");

    assertThat(cancelled).isPresent();
    assertThat(cancelled.get().status()).isEqualTo(TicketStatus.CANCELLED);
    verify(hashOps).put("mm:ticket:ticket-1", "status", "CANCELLED");
    verify(zSetOps).remove("mm:queue:casual", "ticket-1");
  }

  @SuppressWarnings("unchecked")
  @Test
  void queueDepthAndOldestAgeAreReadFromZSet() {
    final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    final ZSetOperations<String, String> zSetOps = Mockito.mock(ZSetOperations.class);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    when(zSetOps.size("mm:queue:casual")).thenReturn(3L);
    when(zSetOps.rangeWithScores("mm:queue:casual", 0, 0))
        .thenReturn(
            Set.of(
                new org.springframework.data.redis.core.DefaultTypedTuple<>(
                    "ticket-1", (double) Instant.now().minusSeconds(8).toEpochMilli())));

    final RedisMatchmakingTicketRepository repository =
        new RedisMatchmakingTicketRepository(redisTemplate);

    assertThat(repository.queueDepth(MatchMode.CASUAL)).isEqualTo(3L);
    assertThat(repository.oldestQueueAgeSeconds(MatchMode.CASUAL)).isPresent();
    assertThat(repository.oldestQueueAgeSeconds(MatchMode.CASUAL).get()).isGreaterThanOrEqualTo(7L);
  }

  @SuppressWarnings("unchecked")
  @Test
  void matchRepositoryMarksMatchedForTwoQueuedTickets() {
    final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    final ZSetOperations<String, String> zSetOps = Mockito.mock(ZSetOperations.class);
    final HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    when(redisTemplate.opsForHash()).thenReturn(hashOps);
    when(zSetOps.popMin("mm:queue:casual"))
        .thenReturn(new org.springframework.data.redis.core.DefaultTypedTuple<>("ticket-1", 1.0))
        .thenReturn(new org.springframework.data.redis.core.DefaultTypedTuple<>("ticket-2", 2.0));
    when(hashOps.entries("mm:ticket:ticket-1"))
        .thenReturn(
            Map.of("status", "QUEUED", "expires_at", Instant.now().plusSeconds(30).toString()));
    when(hashOps.entries("mm:ticket:ticket-2"))
        .thenReturn(
            Map.of("status", "QUEUED", "expires_at", Instant.now().plusSeconds(30).toString()));

    final RedisLuaMatchmakingMatchRepository repository =
        new RedisLuaMatchmakingMatchRepository(redisTemplate);

    final Optional<com.example.matchmaking.model.MatchPair> result =
        repository.matchTwo(MatchMode.CASUAL, Instant.now());

    assertThat(result).isPresent();
    assertThat(result.get().mode()).isEqualTo(MatchMode.CASUAL);
    verify(hashOps).put(eq("mm:ticket:ticket-1"), eq("status"), eq("MATCHED"));
    verify(hashOps).put(eq("mm:ticket:ticket-2"), eq("status"), eq("MATCHED"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void matchRepositoryReturnsEmptyWhenInsufficientQueuedTickets() {
    final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    final ZSetOperations<String, String> zSetOps = Mockito.mock(ZSetOperations.class);
    final HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    when(redisTemplate.opsForHash()).thenReturn(hashOps);
    when(zSetOps.popMin("mm:queue:casual"))
        .thenReturn(new org.springframework.data.redis.core.DefaultTypedTuple<>("ticket-1", 1.0))
        .thenReturn(null);
    when(hashOps.entries("mm:ticket:ticket-1"))
        .thenReturn(
            Map.of("status", "QUEUED", "expires_at", Instant.now().plusSeconds(30).toString()));

    final RedisLuaMatchmakingMatchRepository repository =
        new RedisLuaMatchmakingMatchRepository(redisTemplate);

    assertThat(repository.matchTwo(MatchMode.CASUAL, Instant.now())).isEmpty();
  }
}
