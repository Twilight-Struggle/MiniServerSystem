/*
 * どこで: Matchmaking サービス層
 * 何を: キュー深さ・最古待機時間・マッチ結果・TTM を計測する
 * なぜ: SLI/SLO 運用に必要な指標をアプリ内で直接観測するため
 */
package com.example.matchmaking.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MatchmakingMetrics {

  private final MeterRegistry meterRegistry;

  public MatchmakingMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * 役割: mode ごとの queue depth を Gauge 等へ反映する。
   * 動作: ワーカーループや API 経由で観測した深さをメトリクスに保存する。
   * 前提: depth は 0 以上。
   */
  public void updateQueueDepth(String mode, long depth) {
    throw new UnsupportedOperationException("TODO: implement updateQueueDepth");
  }

  /**
   * 役割: mode ごとの最古待機時間を反映する。
   * 動作: キューが空でないときに oldest age を更新し、空なら 0 として扱う。
   * 前提: ageSeconds は 0 以上。
   */
  public void updateOldestQueueAge(String mode, long ageSeconds) {
    throw new UnsupportedOperationException("TODO: implement updateOldestQueueAge");
  }

  /**
   * 役割: マッチ処理結果件数をカウントする。
   * 動作: `matched|expired|cancelled|error` などの result タグでインクリメントする。
   * 前提: result は運用で定義した定数を渡す。
   */
  public void recordMatchResult(String result) {
    throw new UnsupportedOperationException("TODO: implement recordMatchResult");
  }

  /**
   * 役割: ticket 作成から match 成立までの遅延(TTM)を記録する。
   * 動作: createdAt <= matchedAt の場合にのみ Timer へ記録し、逆転時刻は破棄する。
   * 前提: 引数は ISO-8601 文字列などへ変換可能な時刻であること。
   */
  public void recordTimeToMatchSeconds(long seconds) {
    throw new UnsupportedOperationException("TODO: implement recordTimeToMatchSeconds");
  }

  /**
   * 役割: Redis/Lua 実行失敗など依存障害をカウントする。
   * 動作: エラー種別タグを付与して件数を増やす。
   * 前提: errorType は空でないこと。
   */
  public void recordDependencyError(String errorType) {
    throw new UnsupportedOperationException("TODO: implement recordDependencyError");
  }
}
