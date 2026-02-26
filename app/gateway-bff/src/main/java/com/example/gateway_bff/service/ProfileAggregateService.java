package com.example.gateway_bff.service;

import com.example.gateway_bff.api.response.ProfileAggregateResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.dto.EntitlementSummaryResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileAggregateService {

  private final AccountUserClient accountUserClient;
  private final EntitlementClient entitlementClient;
  private final MatchmakingClient matchmakingClient;

  public ProfileAggregateResponse aggregateByUserId(
      String userId, AuthenticatedUser requester, String ticketId) {
    validateUserId(userId);
    validateRequester(requester);
    if (!userId.equals(requester.userId())) {
      throw new ProfileAccessDeniedException("profile access denied");
    }
    final var user = accountUserClient.getUser(userId, requester);
    final var entitlements = entitlementClient.getUserEntitlements(userId);
    final Map<String, Object> account = new LinkedHashMap<>();
    account.put("user_id", user.userId());
    putIfNotNull(account, "display_name", user.displayName());
    putIfNotNull(account, "locale", user.locale());
    putIfNotNull(account, "status", user.status());
    account.put("roles", user.roles() == null ? List.of() : user.roles());
    final Map<String, Object> entitlement = new LinkedHashMap<>();
    entitlement.put("user_id", entitlements.userId());
    entitlement.put(
        "entitlements", entitlements.entitlements().stream().map(this::toEntitlementMap).toList());
    final Map<String, Object> matchmaking = toMatchmaking(ticketId, requester.userId());
    return new ProfileAggregateResponse(account, entitlement, matchmaking);
  }

  private Map<String, Object> toEntitlementMap(EntitlementSummaryResponse summary) {
    final Map<String, Object> entitlement = new LinkedHashMap<>();
    putIfNotNull(entitlement, "stock_keeping_unit", summary.stockKeepingUnit());
    putIfNotNull(entitlement, "status", summary.status());
    entitlement.put("version", summary.version());
    putIfNotNull(entitlement, "updated_at", summary.updatedAt());
    return entitlement;
  }

  private Map<String, Object> toMatchmaking(String ticketId, String requesterUserId) {
    if (ticketId == null || ticketId.isBlank()) {
      return Map.of();
    }
    final var ticket = matchmakingClient.getTicketStatus(ticketId, requesterUserId);
    final Map<String, Object> matched = new LinkedHashMap<>();
    if (ticket.matched() != null) {
      putIfNotNull(matched, "match_id", ticket.matched().matchId());
      matched.put(
          "peer_user_ids",
          ticket.matched().peerUserIds() == null ? List.of() : ticket.matched().peerUserIds());
      matched.put(
          "session", ticket.matched().session() == null ? Map.of() : ticket.matched().session());
    }
    final Map<String, Object> matchmaking = new LinkedHashMap<>();
    putIfNotNull(matchmaking, "ticket_id", ticket.ticketId());
    putIfNotNull(matchmaking, "status", ticket.status());
    putIfNotNull(matchmaking, "expires_at", ticket.expiresAt());
    matchmaking.put("matched", matched);
    return matchmaking;
  }

  private void validateUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId is required");
    }
  }

  private void validateRequester(AuthenticatedUser requester) {
    if (requester == null || requester.userId() == null || requester.userId().isBlank()) {
      throw new IllegalArgumentException("requester userId is required");
    }
  }

  private void putIfNotNull(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value);
    }
  }
}
