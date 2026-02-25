package com.example.matchmaking.repository;

import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.MatchPair;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisLuaMatchmakingMatchRepository implements MatchmakingMatchRepository {

  private static final String LUA_PATH = "lua/match_two.lua";
  private static final String RESULT_MATCHED = "matched";

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "StringRedisTemplate は Spring 管理の共有コンポーネントで防御的コピーが不可能なため")
  private final StringRedisTemplate redisTemplate;

  private final RedisScript<List> matchTwoScript;

  public RedisLuaMatchmakingMatchRepository(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
    final DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(LUA_PATH));
    script.setResultType(List.class);
    this.matchTwoScript = script;
  }

  @Override
  public Optional<MatchPair> matchTwo(MatchMode mode, Instant matchedAt) {
    final String queueKey = RedisMatchmakingTicketRepository.queueKey(mode);
    final String matchId = UUID.randomUUID().toString();
    final List<?> result =
        redisTemplate.execute(
            matchTwoScript, List.of(queueKey), String.valueOf(matchedAt.toEpochMilli()), matchId);
    if (result == null || result.size() < 4) {
      return Optional.empty();
    }
    if (!RESULT_MATCHED.equals(String.valueOf(result.get(0)))) {
      return Optional.empty();
    }
    return Optional.of(
        new MatchPair(
            String.valueOf(result.get(3)),
            mode,
            String.valueOf(result.get(1)),
            String.valueOf(result.get(2)),
            matchedAt));
  }
}
