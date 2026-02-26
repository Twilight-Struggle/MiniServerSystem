/*
 * どこで: Gateway-BFF API 層テスト
 * 何を: 例外ハンドラが ACCOUNT 系エラーコードをメトリクス記録することを検証する
 * なぜ: 障害時のエラー種別カウントが欠落しないことを保証するため
 */
package com.example.gateway_bff.api;

import static org.mockito.Mockito.verify;

import com.example.gateway_bff.service.AccountInactiveException;
import com.example.gateway_bff.service.AccountIntegrationException;
import com.example.gateway_bff.service.EntitlementIntegrationException;
import com.example.gateway_bff.service.GatewayMetrics;
import com.example.gateway_bff.service.MatchmakingIntegrationException;
import com.example.gateway_bff.service.ProfileAccessDeniedException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GatewayApiExceptionHandlerTest {

  @Test
  void handleAccountInactiveRecordsMetric() {
    final GatewayMetrics metrics = Mockito.mock(GatewayMetrics.class);
    final GatewayApiExceptionHandler handler = new GatewayApiExceptionHandler(metrics);

    handler.handleAccountInactive(new AccountInactiveException("inactive"));

    verify(metrics).recordAccountIntegrationError("ACCOUNT_INACTIVE");
  }

  @Test
  void handleAccountIntegrationRecordsReasonSpecificMetric() {
    final GatewayMetrics metrics = Mockito.mock(GatewayMetrics.class);
    final GatewayApiExceptionHandler handler = new GatewayApiExceptionHandler(metrics);

    handler.handleAccountIntegration(
        new AccountIntegrationException(
            AccountIntegrationException.Reason.TIMEOUT, "account timeout"));
    handler.handleAccountIntegration(
        new AccountIntegrationException(
            AccountIntegrationException.Reason.BAD_GATEWAY, "bad gateway"));

    verify(metrics).recordAccountIntegrationError("ACCOUNT_TIMEOUT");
    verify(metrics).recordAccountIntegrationError("ACCOUNT_BAD_GATEWAY");
  }

  @Test
  void handleMatchmakingIntegrationRecordsReasonSpecificMetric() {
    final GatewayMetrics metrics = Mockito.mock(GatewayMetrics.class);
    final GatewayApiExceptionHandler handler = new GatewayApiExceptionHandler(metrics);

    handler.handleMatchmakingIntegration(
        new MatchmakingIntegrationException(
            MatchmakingIntegrationException.Reason.TIMEOUT, "timeout"));
    handler.handleMatchmakingIntegration(
        new MatchmakingIntegrationException(
            MatchmakingIntegrationException.Reason.BAD_GATEWAY, "bad gateway"));

    verify(metrics).recordAccountIntegrationError("MATCHMAKING_TIMEOUT");
    verify(metrics).recordAccountIntegrationError("MATCHMAKING_BAD_GATEWAY");
  }

  @Test
  void handleEntitlementIntegrationRecordsReasonSpecificMetric() {
    final GatewayMetrics metrics = Mockito.mock(GatewayMetrics.class);
    final GatewayApiExceptionHandler handler = new GatewayApiExceptionHandler(metrics);

    handler.handleEntitlementIntegration(
        new EntitlementIntegrationException(
            EntitlementIntegrationException.Reason.TIMEOUT, "timeout"));
    handler.handleEntitlementIntegration(
        new EntitlementIntegrationException(
            EntitlementIntegrationException.Reason.BAD_GATEWAY, "bad gateway"));

    verify(metrics).recordAccountIntegrationError("ENTITLEMENT_TIMEOUT");
    verify(metrics).recordAccountIntegrationError("ENTITLEMENT_BAD_GATEWAY");
  }

  @Test
  void handleProfileAccessDeniedRecordsMetric() {
    final GatewayMetrics metrics = Mockito.mock(GatewayMetrics.class);
    final GatewayApiExceptionHandler handler = new GatewayApiExceptionHandler(metrics);

    handler.handleProfileAccessDenied(new ProfileAccessDeniedException("denied"));

    verify(metrics).recordAccountIntegrationError("PROFILE_FORBIDDEN");
  }
}
