package com.example.matchmaking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.TicketStatus;
import com.example.matchmaking.service.MatchmakingMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

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
    final MatchmakingMetrics metrics = Mockito.mock(MatchmakingMetrics.class);

    final RedisMatchmakingTicketRepository repository =
        new RedisMatchmakingTicketRepository(redisTemplate, metrics);

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
    final ArgumentCaptor<Map<String, String>> fieldsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(hashOps).putAll(eq("mm:ticket:" + ticket.ticketId()), fieldsCaptor.capture());
    assertThat(fieldsCaptor.getValue()).containsKey("expires_at_epoch_millis");
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
                "expires_at", Instant.now().plusSeconds(60).toString(),
                "attributes", "{}"));

    final MatchmakingMetrics metrics = Mockito.mock(MatchmakingMetrics.class);

    final RedisMatchmakingTicketRepository repository =
        new RedisMatchmakingTicketRepository(redisTemplate, metrics);

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

    final MatchmakingMetrics metrics = Mockito.mock(MatchmakingMetrics.class);

    final RedisMatchmakingTicketRepository repository =
        new RedisMatchmakingTicketRepository(redisTemplate, metrics);

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
    final MatchmakingMetrics metrics = Mockito.mock(MatchmakingMetrics.class);

    final RedisMatchmakingTicketRepository repository =
        new RedisMatchmakingTicketRepository(redisTemplate, metrics);

    assertThat(repository.queueDepth(MatchMode.CASUAL)).isEqualTo(3L);
    assertThat(repository.oldestQueueAgeSeconds(MatchMode.CASUAL)).isPresent();
    assertThat(repository.oldestQueueAgeSeconds(MatchMode.CASUAL).get()).isGreaterThanOrEqualTo(7L);
  }

  @SuppressWarnings("unchecked")
  @Test
  void findTicketByIdMarksExpiredAndRecordsMetric() {
    final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    final HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
    final ZSetOperations<String, String> zSetOps = Mockito.mock(ZSetOperations.class);
    final MatchmakingMetrics metrics = Mockito.mock(MatchmakingMetrics.class);
    when(redisTemplate.opsForHash()).thenReturn(hashOps);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    when(hashOps.entries("mm:ticket:ticket-1"))
        .thenReturn(
            Map.of(
                "user_id", "user-1",
                "mode", "casual",
                "status", "QUEUED",
                "created_at", "2026-02-24T12:00:00Z",
                "expires_at", Instant.now().minusSeconds(30).toString(),
                "attributes", "{}"));

    final RedisMatchmakingTicketRepository repository =
        new RedisMatchmakingTicketRepository(redisTemplate, metrics);

    final var ticket = repository.findTicketById("ticket-1");

    assertThat(ticket).isPresent();
    assertThat(ticket.get().status()).isEqualTo(TicketStatus.EXPIRED);
    verify(hashOps).put("mm:ticket:ticket-1", "status", "EXPIRED");
    verify(zSetOps).remove("mm:queue:casual", "ticket-1");
    verify(metrics).recordMatchResult("expired");
  }

  @SuppressWarnings("unchecked")
  @Test
  void matchRepositoryMarksMatchedForTwoQueuedTickets() {
    final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    final List<Object> scriptResult = List.of("matched", "ticket-1", "ticket-2", "match-1");
    Mockito.doReturn(scriptResult)
        .when(redisTemplate)
        .execute(Mockito.<RedisScript<List>>any(), eq(List.of("mm:queue:casual")), any(), any());

    final RedisLuaMatchmakingMatchRepository repository =
        new RedisLuaMatchmakingMatchRepository(redisTemplate);

    final Optional<com.example.matchmaking.model.MatchPair> result =
        repository.matchTwo(MatchMode.CASUAL, Instant.now());

    assertThat(result).isPresent();
    assertThat(result.get().mode()).isEqualTo(MatchMode.CASUAL);
    assertThat(result.get().ticketId1()).isEqualTo("ticket-1");
    assertThat(result.get().ticketId2()).isEqualTo("ticket-2");
    assertThat(result.get().matchId()).isEqualTo("match-1");
  }

  @SuppressWarnings("unchecked")
  @Test
  void matchRepositoryReturnsEmptyWhenInsufficientQueuedTickets() {
    final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    Mockito.doReturn(List.of("no_match"))
        .when(redisTemplate)
        .execute(Mockito.<RedisScript<List>>any(), eq(List.of("mm:queue:casual")), any(), any());

    final RedisLuaMatchmakingMatchRepository repository =
        new RedisLuaMatchmakingMatchRepository(redisTemplate);

    assertThat(repository.matchTwo(MatchMode.CASUAL, Instant.now())).isEmpty();
  }
}
