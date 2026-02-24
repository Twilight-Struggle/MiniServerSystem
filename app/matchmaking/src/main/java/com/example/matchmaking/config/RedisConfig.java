/*
 * どこで: Matchmaking インフラ設定
 * 何を: Redis 操作で利用する StringRedisTemplate を提供する
 * なぜ: Repository が Redis へアクセスできるようにするため
 */
package com.example.matchmaking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

  @Bean
  StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
    return new StringRedisTemplate(connectionFactory);
  }
}
