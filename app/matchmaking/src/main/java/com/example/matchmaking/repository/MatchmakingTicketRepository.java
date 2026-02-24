/*
 * どこで: Matchmaking Repository 層
 * 何を: ticket/queue/idempotency の永続化操作を抽象化する
 * なぜ: Redis 実装詳細を service から切り離すため
 */
package com.example.matchmaking.repository;

import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.TicketRecord;
import java.time.Duration;
import java.util.Optional;

public interface MatchmakingTicketRepository {

  /**
   * 役割: Join 時に冪等キーを評価し、新規 ticket を作るか既存 ticket を返す。 動作: 既存 idempotency key が有効なら既存 ticket
   * を返し、無効/未登録なら新規 ticket を作成する。 前提: mode, userId, idempotencyKey は空でないこと。
   */
  TicketRecord createOrReuseTicket(
      MatchMode mode,
      String userId,
      String idempotencyKey,
      String attributesJson,
      Duration ticketTtl,
      Duration idempotencyTtl);

  /**
   * 役割: ticketId からチケット状態を取得する。 動作: ticket hash が存在する場合は record を返し、存在しなければ empty を返す。 前提: ticketId
   * は空でないこと。
   */
  Optional<TicketRecord> findTicketById(String ticketId);

  /**
   * 役割: キャンセル要求を適用する。 動作: status=QUEUED の場合は CANCELLED へ遷移し、既に終端状態ならそのまま返す。 前提: ticketId は空でないこと。
   */
  Optional<TicketRecord> cancelTicket(String ticketId);

  /** 役割: Queue の深さを返す。 動作: mode ごとの Sorted Set の要素数を返す。 前提: mode は null でないこと。 */
  long queueDepth(MatchMode mode);

  /**
   * 役割: Queue 先頭 ticket の経過時間を取得する。 動作: 先頭 ticket の enqueue 時刻が取れれば秒数を返し、空キューなら empty を返す。 前提: mode
   * は null でないこと。
   */
  Optional<Long> oldestQueueAgeSeconds(MatchMode mode);
}
