/*
 * どこで: Matchmaking ワーカー
 * 何を: 定期的に queue を走査してマッチ成立処理を行う
 * なぜ: ticket API と非同期マッチング処理を分離するため
 */
package com.example.matchmaking.worker;

import com.example.matchmaking.config.MatchmakingProperties;
import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.service.MatchmakingMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "matchmaking.worker-enabled", havingValue = "true", matchIfMissing = true)
public class MatchmakerWorker {

  private final MatchmakingMetrics metrics;
  private final MatchmakingProperties properties;

  public MatchmakerWorker(MatchmakingMetrics metrics, MatchmakingProperties properties) {
    this.metrics = metrics;
    this.properties = properties;
  }

  /**
   * 役割: worker の 1 サイクルを実行する。
   * 動作: mode ごとに queue depth を確認し、2件以上ある場合に Lua マッチ処理を呼び出す。
   * 前提: Redis 接続が利用可能であること。
   */
  @Scheduled(fixedDelayString = "${matchmaking.worker-poll-interval}")
  public void run() {
    for (MatchMode mode : MatchMode.values()) {
      // TODO: queue depth 更新、match 処理呼び出し、publish、メトリクス更新を実装する。
      metrics.updateQueueDepth(mode.value(), 0);
    }
  }
}
