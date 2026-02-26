package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gateway_bff.api.response.UserResponse;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.dto.EntitlementSummaryResponse;
import com.example.gateway_bff.service.dto.EntitlementsResponse;
import com.example.gateway_bff.service.dto.MatchmakingTicketStatusResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileAggregateServiceTest {

  @Test
  void aggregateRejectsBlankUserId() {
    final GatewayMetrics gatewayMetrics = mock(GatewayMetrics.class);
    final ProfileAggregateService service = newService(gatewayMetrics);
    assertThatThrownBy(
            () ->
                service.aggregateByUserId(
                    " ", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), null))
        .isInstanceOf(IllegalArgumentException.class);
    verify(gatewayMetrics).recordProfileAggregateResult("error", "without_ticket");
  }

  @Test
  void aggregateReturnsAccountEntitlementAndMatchmaking() {
    final AccountUserClient accountUserClient = mock(AccountUserClient.class);
    final EntitlementClient entitlementClient = mock(EntitlementClient.class);
    final MatchmakingClient matchmakingClient = mock(MatchmakingClient.class);
    final GatewayMetrics gatewayMetrics = mock(GatewayMetrics.class);
    final ProfileAggregateService service =
        new ProfileAggregateService(
            accountUserClient, entitlementClient, matchmakingClient, gatewayMetrics);
    when(accountUserClient.getUser(any(), any()))
        .thenReturn(new UserResponse("user-1", "name", "ja", "ACTIVE", List.of("USER")));
    when(entitlementClient.getUserEntitlements("user-1"))
        .thenReturn(
            new EntitlementsResponse(
                "user-1",
                List.of(
                    new EntitlementSummaryResponse("sku-1", "ACTIVE", 2, "2026-02-26T00:00:00Z"))));
    when(matchmakingClient.getTicketStatus("ticket-1", "user-1"))
        .thenReturn(
            new MatchmakingTicketStatusResponse(
                "ticket-1", "QUEUED", "2026-02-26T00:01:00Z", null));

    final var response =
        service.aggregateByUserId(
            "user-1", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), "ticket-1");

    assertThat(response.account()).containsEntry("user_id", "user-1");
    assertThat(response.entitlement()).containsEntry("user_id", "user-1");
    assertThat(response.matchmaking()).containsEntry("ticket_id", "ticket-1");
    verify(gatewayMetrics).recordProfileAggregateResult("success", "with_ticket");
    verify(gatewayMetrics)
        .recordProfileAggregateDependencyDuration(eq("account"), eq("success"), any());
    verify(gatewayMetrics)
        .recordProfileAggregateDependencyDuration(eq("entitlement"), eq("success"), any());
    verify(gatewayMetrics)
        .recordProfileAggregateDependencyDuration(eq("matchmaking"), eq("success"), any());
  }

  @Test
  void aggregateRejectsOtherUserProfileAccess() {
    final GatewayMetrics gatewayMetrics = mock(GatewayMetrics.class);
    final ProfileAggregateService service = newService(gatewayMetrics);

    assertThatThrownBy(
            () ->
                service.aggregateByUserId(
                    "user-2", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), null))
        .isInstanceOf(ProfileAccessDeniedException.class);
    verify(gatewayMetrics).recordProfileAggregateResult("error", "without_ticket");
  }

  @Test
  void aggregateSkipsMatchmakingWhenTicketIdIsBlank() {
    final AccountUserClient accountUserClient = mock(AccountUserClient.class);
    final EntitlementClient entitlementClient = mock(EntitlementClient.class);
    final MatchmakingClient matchmakingClient = mock(MatchmakingClient.class);
    final GatewayMetrics gatewayMetrics = mock(GatewayMetrics.class);
    final ProfileAggregateService service =
        new ProfileAggregateService(
            accountUserClient, entitlementClient, matchmakingClient, gatewayMetrics);
    when(accountUserClient.getUser(any(), any()))
        .thenReturn(new UserResponse("user-1", "name", "ja", "ACTIVE", List.of("USER")));
    when(entitlementClient.getUserEntitlements("user-1"))
        .thenReturn(new EntitlementsResponse("user-1", List.of()));

    final var response =
        service.aggregateByUserId(
            "user-1", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), " ");

    assertThat(response.matchmaking()).isEqualTo(Map.of());
    verify(matchmakingClient, never()).getTicketStatus(any(), any());
    verify(gatewayMetrics).recordProfileAggregateResult("success", "without_ticket");
  }

  @Test
  void aggregateRecordsDependencyErrorWhenEntitlementFails() {
    final AccountUserClient accountUserClient = mock(AccountUserClient.class);
    final EntitlementClient entitlementClient = mock(EntitlementClient.class);
    final MatchmakingClient matchmakingClient = mock(MatchmakingClient.class);
    final GatewayMetrics gatewayMetrics = mock(GatewayMetrics.class);
    final ProfileAggregateService service =
        new ProfileAggregateService(
            accountUserClient, entitlementClient, matchmakingClient, gatewayMetrics);
    when(accountUserClient.getUser(any(), any()))
        .thenReturn(new UserResponse("user-1", "name", "ja", "ACTIVE", List.of("USER")));
    when(entitlementClient.getUserEntitlements("user-1"))
        .thenThrow(
            new EntitlementIntegrationException(
                EntitlementIntegrationException.Reason.TIMEOUT, "timeout"));

    assertThatThrownBy(
            () ->
                service.aggregateByUserId(
                    "user-1", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), null))
        .isInstanceOf(EntitlementIntegrationException.class);
    verify(gatewayMetrics).recordProfileAggregateResult("error", "without_ticket");
    verify(gatewayMetrics)
        .recordProfileAggregateDependencyDuration(eq("entitlement"), eq("error"), any());
    verify(gatewayMetrics)
        .recordProfileAggregateDependencyError("entitlement", "ENTITLEMENT_TIMEOUT");
  }

  private ProfileAggregateService newService(GatewayMetrics gatewayMetrics) {
    return new ProfileAggregateService(
        mock(AccountUserClient.class),
        mock(EntitlementClient.class),
        mock(MatchmakingClient.class),
        gatewayMetrics);
  }
}
