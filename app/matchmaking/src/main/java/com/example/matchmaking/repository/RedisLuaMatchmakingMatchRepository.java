/*
 * どこで: Matchmaking Repository 層
 * 何を: Lua スクリプト経由の原子マッチ処理を提供する
 * なぜ: queue pop と ticket 更新を 1 トランザクションで扱うため
 */
package com.example.matchmaking.repository;

import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.MatchPair;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisLuaMatchmakingMatchRepository implements MatchmakingMatchRepository {

  private final StringRedisTemplate redisTemplate;

  public RedisLuaMatchmakingMatchRepository(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Optional<MatchPair> matchTwo(MatchMode mode, Instant matchedAt) {
    throw new UnsupportedOperationException("TODO: implement matchTwo via lua");
  }
}
