/*
 * どこで: Matchmaking 設定
 * 何を: matchmaking イベント publish 設定を保持する
 * なぜ: subject/stream の運用切り替えを容易にするため
 */
package com.example.matchmaking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "matchmaking.nats")
public record MatchmakingNatsProperties(String subject, String stream, Duration duplicateWindow) {}
