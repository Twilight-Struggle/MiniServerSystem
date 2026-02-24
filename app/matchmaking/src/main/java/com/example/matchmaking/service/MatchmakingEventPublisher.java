/*
 * どこで: Matchmaking サービス層
 * 何を: match 成立イベントを NATS JetStream に publish する
 * なぜ: notification サービスへ非同期通知するため
 */
package com.example.matchmaking.service;

import com.example.matchmaking.model.MatchPair;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true", matchIfMissing = true)
public class MatchmakingEventPublisher {

  /**
   * 役割: 1 件の match 成立イベントを publish する。
   * 動作: event_id を生成して `Nats-Msg-Id` に設定し、subject へ publish する。
   * 前提: pair は null でなく、matchId を持つこと。
   */
  public void publishMatched(MatchPair pair) {
    throw new UnsupportedOperationException("TODO: implement publishMatched");
  }
}
