package com.example.matchmaking.repository;

import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.MatchPair;
import com.example.matchmaking.model.TicketStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

@Repository
public class RedisLuaMatchmakingMatchRepository implements MatchmakingMatchRepository {

  private static final String FIELD_STATUS = "status";
  private static final String FIELD_EXPIRES_AT = "expires_at";
  private static final String FIELD_MATCH_ID = "match_id";

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "StringRedisTemplate は Spring 管理の共有コンポーネントで防御的コピーが不可能なため")
  private final StringRedisTemplate redisTemplate;

  public RedisLuaMatchmakingMatchRepository(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Optional<MatchPair> matchTwo(MatchMode mode, Instant matchedAt) {
    final String queueKey = RedisMatchmakingTicketRepository.queueKey(mode);
    final List<String> valid = new ArrayList<>();

    for (int i = 0; i < 20 && valid.size() < 2; i++) {
      final ZSetOperations.TypedTuple<String> popped = redisTemplate.opsForZSet().popMin(queueKey);
      if (popped == null || popped.getValue() == null) {
        break;
      }
      final String ticketId = popped.getValue();
      final Map<Object, Object> fields =
          redisTemplate.opsForHash().entries(RedisMatchmakingTicketRepository.ticketKey(ticketId));
      if (!isMatchable(fields, matchedAt)) {
        continue;
      }
      valid.add(ticketId);
    }

    if (valid.size() < 2) {
      for (String ticketId : valid) {
        redisTemplate.opsForZSet().add(queueKey, ticketId, matchedAt.toEpochMilli());
      }
      return Optional.empty();
    }

    final String matchId = UUID.randomUUID().toString();
    for (String ticketId : valid) {
      final String ticketKey = RedisMatchmakingTicketRepository.ticketKey(ticketId);
      redisTemplate.opsForHash().put(ticketKey, FIELD_STATUS, TicketStatus.MATCHED.name());
      redisTemplate.opsForHash().put(ticketKey, FIELD_MATCH_ID, matchId);
    }

    return Optional.of(new MatchPair(matchId, mode, valid.get(0), valid.get(1), matchedAt));
  }

  private boolean isMatchable(Map<Object, Object> fields, Instant now) {
    if (fields == null || fields.isEmpty()) {
      return false;
    }
    final String status = stringValue(fields.get(FIELD_STATUS));
    if (!TicketStatus.QUEUED.name().equals(status)) {
      return false;
    }
    final String expiresAt = stringValue(fields.get(FIELD_EXPIRES_AT));
    if (expiresAt == null || expiresAt.isBlank()) {
      return false;
    }
    return now.isBefore(Instant.parse(expiresAt));
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
