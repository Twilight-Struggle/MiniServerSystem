/*
 * どこで: Matchmaking 設定
 * 何を: NATS 接続設定を保持する
 * なぜ: publish の有効/無効や接続先を環境で切り替えるため
 */
package com.example.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nats")
public record NatsProperties(boolean enabled, String url, Integer connectionTimeout) {}
