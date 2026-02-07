/*
 * どこで: Notification API モデル
 * 何を: デバッグ用通知一覧のレスポンス
 * なぜ: API レスポンスの構造を固定するため
 */
package com.example.notification.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NotificationInboxResponse(String userId, List<NotificationSummary> notifications) {
  public NotificationInboxResponse {
    // SpotBugs の EI_EXPOSE_REP 対応: 受け取ったリストを防御的コピーして不変化する
    if (notifications != null) {
      notifications = Collections.unmodifiableList(new ArrayList<>(notifications));
    }
  }

  @Override
  public List<NotificationSummary> notifications() {
    // SpotBugs の EI_EXPOSE_REP 対応: 内部の不変リストを直接返さず毎回コピーして返す
    if (notifications == null) {
      return null;
    }
    return Collections.unmodifiableList(new ArrayList<>(notifications));
  }
}
