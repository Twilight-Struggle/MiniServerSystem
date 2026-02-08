/*
 * どこで: Notification アプリの設定バインド
 * 何を: NATS 接続設定をプロパティから読み込む
 * なぜ: 環境ごとの接続先を安全に切り替えるため
 */
package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nats")
public record NatsProperties(boolean enabled, String url, Integer connectionTimeout) {}
