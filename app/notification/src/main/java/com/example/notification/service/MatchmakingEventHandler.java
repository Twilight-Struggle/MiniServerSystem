/*
 * どこで: Notification サービス層
 * 何を: matchmaking イベントを notifications テーブルへ反映する
 * なぜ: match 成立通知を既存通知基盤へ統合するため
 */
package com.example.notification.service;

import com.example.proto.matchmaking.MatchmakingEvent;
import org.springframework.stereotype.Service;

@Service
public class MatchmakingEventHandler {

  /**
   * 役割: マッチ成立イベントを永続化する。
   * 動作: processed_events の冪等チェック後、notifications に PENDING レコードを作成する。
   * 前提: event_id, occurred_at, match_id が有効形式であること。
   */
  public void handleMatchmakingEvent(MatchmakingEvent event) {
    throw new UnsupportedOperationException("TODO: implement handleMatchmakingEvent");
  }
}
