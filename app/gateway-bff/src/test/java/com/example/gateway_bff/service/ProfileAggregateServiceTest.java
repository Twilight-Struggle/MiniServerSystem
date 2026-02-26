package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    final ProfileAggregateService service = newService();
    assertThatThrownBy(
            () ->
                service.aggregateByUserId(
                    " ", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aggregateReturnsAccountEntitlementAndMatchmaking() {
    final AccountUserClient accountUserClient = mock(AccountUserClient.class);
    final EntitlementClient entitlementClient = mock(EntitlementClient.class);
    final MatchmakingClient matchmakingClient = mock(MatchmakingClient.class);
    final ProfileAggregateService service =
        new ProfileAggregateService(accountUserClient, entitlementClient, matchmakingClient);
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
  }

  @Test
  void aggregateRejectsOtherUserProfileAccess() {
    final ProfileAggregateService service = newService();

    assertThatThrownBy(
            () ->
                service.aggregateByUserId(
                    "user-2", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), null))
        .isInstanceOf(ProfileAccessDeniedException.class);
  }

  @Test
  void aggregateSkipsMatchmakingWhenTicketIdIsBlank() {
    final AccountUserClient accountUserClient = mock(AccountUserClient.class);
    final EntitlementClient entitlementClient = mock(EntitlementClient.class);
    final MatchmakingClient matchmakingClient = mock(MatchmakingClient.class);
    final ProfileAggregateService service =
        new ProfileAggregateService(accountUserClient, entitlementClient, matchmakingClient);
    when(accountUserClient.getUser(any(), any()))
        .thenReturn(new UserResponse("user-1", "name", "ja", "ACTIVE", List.of("USER")));
    when(entitlementClient.getUserEntitlements("user-1"))
        .thenReturn(new EntitlementsResponse("user-1", List.of()));

    final var response =
        service.aggregateByUserId(
            "user-1", new AuthenticatedUser("user-1", "ACTIVE", List.of("USER")), " ");

    assertThat(response.matchmaking()).isEqualTo(Map.of());
    verify(matchmakingClient, never()).getTicketStatus(any(), any());
  }

  private ProfileAggregateService newService() {
    return new ProfileAggregateService(
        mock(AccountUserClient.class),
        mock(EntitlementClient.class),
        mock(MatchmakingClient.class));
  }
}
