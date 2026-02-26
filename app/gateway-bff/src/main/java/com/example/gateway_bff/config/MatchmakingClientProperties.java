/*
 * どこで: Gateway-BFF 設定
 * 何を: matchmaking サービス呼び出し設定を保持する
 * なぜ: BFF からの下流 URL/ヘッダー/パスを外部化するため
 */
package com.example.gateway_bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "matchmaking")
public record MatchmakingClientProperties(
    String baseUrl,
    String joinTicketPath,
    String getTicketPath,
    String cancelTicketPath,
    String userIdHeaderName) {

  public MatchmakingClientProperties {
    baseUrl = baseUrl == null ? "http://matchmaking:80" : baseUrl;
    joinTicketPath =
        joinTicketPath == null || joinTicketPath.isBlank()
            ? "/v1/matchmaking/queues/{mode}/tickets"
            : joinTicketPath;
    getTicketPath =
        getTicketPath == null || getTicketPath.isBlank()
            ? "/v1/matchmaking/tickets/{ticketId}"
            : getTicketPath;
    cancelTicketPath =
        cancelTicketPath == null || cancelTicketPath.isBlank()
            ? "/v1/matchmaking/tickets/{ticketId}"
            : cancelTicketPath;
    userIdHeaderName =
        userIdHeaderName == null || userIdHeaderName.isBlank() ? "X-User-Id" : userIdHeaderName;
  }
}
