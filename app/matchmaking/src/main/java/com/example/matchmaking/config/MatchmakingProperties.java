/*
 * どこで: Matchmaking 設定
 * 何を: ticket/worker/idempotency のドメイン設定を保持する
 * なぜ: 環境差分をコード外へ出し、TDDで上書きしやすくするため
 */
package com.example.matchmaking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "matchmaking")
public record MatchmakingProperties(
    Duration ticketTtl,
    Duration idempotencyTtl,
    Duration workerPollInterval,
    int workerBatchSize,
    boolean workerEnabled) {}
