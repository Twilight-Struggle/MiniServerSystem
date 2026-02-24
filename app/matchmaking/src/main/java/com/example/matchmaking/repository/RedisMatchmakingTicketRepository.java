package com.example.matchmaking.repository;

import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.TicketRecord;
import com.example.matchmaking.model.TicketStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

@Repository
public class RedisMatchmakingTicketRepository implements MatchmakingTicketRepository {

  private static final String FIELD_USER_ID = "user_id";
  private static final String FIELD_MODE = "mode";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_CREATED_AT = "created_at";
  private static final String FIELD_EXPIRES_AT = "expires_at";
  private static final String FIELD_ATTRIBUTES = "attributes";
  private static final String FIELD_MATCH_ID = "match_id";

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "StringRedisTemplate は Spring 管理の共有コンポーネントで防御的コピーが不可能なため")
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
    final String idempotencyRedisKey = idempotencyKey(userId, mode, idempotencyKey);
    final ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
    final String existingTicketId = valueOps.get(idempotencyRedisKey);
    if (existingTicketId != null && !existingTicketId.isBlank()) {
      final Optional<TicketRecord> existing = findTicketById(existingTicketId);
      if (existing.isPresent()) {
        return existing.get();
      }
    }

    final Instant now = Instant.now();
    final Instant expiresAt = now.plus(ticketTtl);
    final String ticketId = UUID.randomUUID().toString();
    final String ticketKey = ticketKey(ticketId);

    final Map<String, String> fields = new HashMap<>();
    fields.put(FIELD_USER_ID, userId);
    fields.put(FIELD_MODE, mode.value());
    fields.put(FIELD_STATUS, TicketStatus.QUEUED.name());
    fields.put(FIELD_CREATED_AT, now.toString());
    fields.put(FIELD_EXPIRES_AT, expiresAt.toString());
    fields.put(FIELD_ATTRIBUTES, attributesJson == null ? "{}" : attributesJson);

    redisTemplate.opsForHash().putAll(ticketKey, fields);
    redisTemplate.expire(ticketKey, ticketTtl);
    redisTemplate.opsForZSet().add(queueKey(mode), ticketId, now.toEpochMilli());
    valueOps.set(idempotencyRedisKey, ticketId, idempotencyTtl);

    return new TicketRecord(
        ticketId,
        userId,
        mode,
        TicketStatus.QUEUED,
        now,
        expiresAt,
        fields.get(FIELD_ATTRIBUTES),
        null);
  }

  @Override
  public Optional<TicketRecord> findTicketById(String ticketId) {
    final Map<Object, Object> raw = redisTemplate.opsForHash().entries(ticketKey(ticketId));
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    final Map<String, String> fields = normalizeFields(raw);
    TicketStatus status = parseStatus(fields.get(FIELD_STATUS));
    final Instant expiresAt = parseInstant(fields.get(FIELD_EXPIRES_AT));
    if (status == TicketStatus.QUEUED && expiresAt != null && !Instant.now().isBefore(expiresAt)) {
      status = TicketStatus.EXPIRED;
    }

    return Optional.of(
        new TicketRecord(
            ticketId,
            fields.get(FIELD_USER_ID),
            MatchMode.fromValue(fields.get(FIELD_MODE)),
            status,
            parseInstant(fields.get(FIELD_CREATED_AT)),
            expiresAt,
            fields.getOrDefault(FIELD_ATTRIBUTES, "{}"),
            fields.get(FIELD_MATCH_ID)));
  }

  @Override
  public Optional<TicketRecord> cancelTicket(String ticketId) {
    final Optional<TicketRecord> existing = findTicketById(ticketId);
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    final TicketRecord record = existing.get();
    if (record.status() != TicketStatus.QUEUED) {
      return existing;
    }

    final String ticketKey = ticketKey(ticketId);
    redisTemplate.opsForHash().put(ticketKey, FIELD_STATUS, TicketStatus.CANCELLED.name());
    redisTemplate.opsForZSet().remove(queueKey(record.mode()), ticketId);

    return Optional.of(
        new TicketRecord(
            record.ticketId(),
            record.userId(),
            record.mode(),
            TicketStatus.CANCELLED,
            record.createdAt(),
            record.expiresAt(),
            record.attributesJson(),
            record.matchId()));
  }

  @Override
  public long queueDepth(MatchMode mode) {
    final Long depth = redisTemplate.opsForZSet().size(queueKey(mode));
    return depth == null ? 0 : depth;
  }

  @Override
  public Optional<Long> oldestQueueAgeSeconds(MatchMode mode) {
    final var values = redisTemplate.opsForZSet().rangeWithScores(queueKey(mode), 0, 0);
    if (values == null || values.isEmpty()) {
      return Optional.empty();
    }
    final ZSetOperations.TypedTuple<String> first = values.iterator().next();
    final Double score = first.getScore();
    if (score == null) {
      return Optional.empty();
    }
    final long ageMillis = Math.max(0, Instant.now().toEpochMilli() - score.longValue());
    return Optional.of(ageMillis / 1000);
  }

  static String ticketKey(String ticketId) {
    return "mm:ticket:" + ticketId;
  }

  static String queueKey(MatchMode mode) {
    return "mm:queue:" + mode.value();
  }

  static String idempotencyKey(String userId, MatchMode mode, String idempotencyKey) {
    return "mm:idemp:" + userId + ":" + mode.value() + ":" + idempotencyKey;
  }

  private Map<String, String> normalizeFields(Map<Object, Object> raw) {
    final Map<String, String> map = new HashMap<>();
    for (Map.Entry<Object, Object> e : raw.entrySet()) {
      map.put(
          String.valueOf(e.getKey()), e.getValue() == null ? null : String.valueOf(e.getValue()));
    }
    return map;
  }

  private TicketStatus parseStatus(String value) {
    return value == null ? TicketStatus.EXPIRED : TicketStatus.valueOf(value);
  }

  private Instant parseInstant(String value) {
    return value == null || value.isBlank() ? null : Instant.parse(value);
  }
}
