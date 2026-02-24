/*
 * どこで: Matchmaking Repository 層
 * 何を: 2件マッチングの原子的処理を抽象化する
 * なぜ: Lua スクリプト実行を Service から分離しテスト容易性を高めるため
 */
package com.example.matchmaking.repository;

import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.MatchPair;
import java.time.Instant;
import java.util.Optional;

public interface MatchmakingMatchRepository {

  /**
   * 役割: queue から 2件を取り出し、MATCHED 更新と match_id 付与を原子的に行う。
   * 動作: 成立時は MatchPair を返し、不成立（人数不足/競合/期限切れのみ）なら empty を返す。
   * 前提: mode は null でなく、matchedAt は現在時刻を渡す。
   */
  Optional<MatchPair> matchTwo(MatchMode mode, Instant matchedAt);
}
