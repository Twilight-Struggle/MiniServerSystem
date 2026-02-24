/*
 * どこで: Matchmaking サービス層
 * 何を: NATS 無効時のダミー publisher を提供する
 * なぜ: ローカルテストで NATS なしでも Service を起動可能にするため
 */
package com.example.matchmaking.service;

import com.example.matchmaking.model.MatchPair;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "nats.enabled", havingValue = "false")
public class NoopMatchmakingEventPublisher {

  /**
   * 役割: NATS 無効時に publish 呼び出しを吸収する。
   * 動作: 何もしない。
   * 前提: なし。
   */
  public void publishMatched(MatchPair pair) {
    // no-op
  }
}
