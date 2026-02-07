/*
 * どこで: 共通ユーティリティ
 * 何を: JDBC で扱う Instant を Timestamp に明示変換する
 * なぜ: PostgreSQL JDBC が Instant の型推論に失敗するケースを回避するため
 */
package com.example.common;

import java.sql.Timestamp;
import java.time.Instant;

public final class JdbcTimestampUtils {
  private JdbcTimestampUtils() {}

  // 前提: Instant は UTC を表現するため Timestamp.from で UTC のまま渡す
  // トレードオフ: DB 側のタイムゾーン設定が UTC 以外でも、アプリは UTC で統一する
  // 理由: JDBC ドライバの型推論に依存せず、常に明示型でバインドするため
  public static Timestamp toTimestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
