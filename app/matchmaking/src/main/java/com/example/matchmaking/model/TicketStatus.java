/*
 * どこで: Matchmaking ドメインモデル
 * 何を: チケット状態を定義する
 * なぜ: API 応答と Redis 永続値を一貫させるため
 */
package com.example.matchmaking.model;

public enum TicketStatus {
  QUEUED,
  MATCHED,
  CANCELLED,
  EXPIRED
}
