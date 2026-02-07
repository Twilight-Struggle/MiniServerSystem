/*
 * どこで: Entitlement API
 * 何を: ユーザ権利一覧のレスポンスを表す
 * なぜ: user_id と entitlements を明示的に返すため
 */
package com.example.entitlement.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EntitlementsResponse(String userId, List<EntitlementSummary> entitlements) {
  public EntitlementsResponse {
    // SpotBugs の EI_EXPOSE_REP 対応: 受け取ったリストを防御的コピーして不変化する
    if (entitlements != null) {
      entitlements = Collections.unmodifiableList(new ArrayList<>(entitlements));
    }
  }

  @Override
  public List<EntitlementSummary> entitlements() {
    // SpotBugs の EI_EXPOSE_REP 対応: 内部の不変リストを直接返さず毎回コピーして返す
    if (entitlements == null) {
      return null;
    }
    return Collections.unmodifiableList(new ArrayList<>(entitlements));
  }
}
