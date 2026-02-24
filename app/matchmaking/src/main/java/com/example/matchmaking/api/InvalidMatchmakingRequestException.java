/*
 * どこで: Matchmaking API
 * 何を: リクエスト妥当性エラーを表現する
 * なぜ: バリデーション失敗を 400 へ正規化するため
 */
package com.example.matchmaking.api;

public class InvalidMatchmakingRequestException extends RuntimeException {
  public InvalidMatchmakingRequestException(String message) {
    super(message);
  }
}
