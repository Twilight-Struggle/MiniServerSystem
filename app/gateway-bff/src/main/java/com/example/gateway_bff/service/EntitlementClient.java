/*
 * どこで: Gateway-BFF サービス層
 * 何を: entitlement サービス呼び出しを担当するクライアント
 * なぜ: profile 集約時に権利一覧を取得するため
 */
package com.example.gateway_bff.service;

import com.example.gateway_bff.config.EntitlementClientProperties;
import com.example.gateway_bff.service.dto.EntitlementsResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class EntitlementClient {

  private static final Logger logger = LoggerFactory.getLogger(EntitlementClient.class);

  private final RestClient entitlementRestClient;
  private final EntitlementClientProperties properties;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "RestClient は Spring 管理の共有コンポーネントで防御的コピーが不可能なため")
  public EntitlementClient(
      RestClient entitlementRestClient, EntitlementClientProperties properties) {
    this.entitlementRestClient = entitlementRestClient;
    this.properties = properties;
  }

  public EntitlementsResponse getUserEntitlements(String userId) {
    validateUserId(userId);
    try {
      return requireResponse(
          entitlementRestClient
              .get()
              .uri(properties.getUserEntitlementsPath(), userId)
              .retrieve()
              .body(EntitlementsResponse.class));
    } catch (RestClientResponseException ex) {
      throw mapResponseException(ex);
    } catch (ResourceAccessException ex) {
      throw mapResourceException(ex);
    } catch (EntitlementIntegrationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      logger.warn("entitlement response parse failed", ex);
      throw new EntitlementIntegrationException(
          EntitlementIntegrationException.Reason.INVALID_RESPONSE,
          "entitlement response parse failed",
          ex);
    }
  }

  private EntitlementsResponse requireResponse(EntitlementsResponse response) {
    if (response == null || isBlank(response.userId())) {
      throw new EntitlementIntegrationException(
          EntitlementIntegrationException.Reason.INVALID_RESPONSE,
          "entitlement response is invalid");
    }
    return response;
  }

  private EntitlementIntegrationException mapResponseException(RestClientResponseException ex) {
    logger.warn(
        "entitlement getUserEntitlements failed with http status={} statusText={}",
        ex.getStatusCode().value(),
        ex.getStatusText());
    if (ex.getStatusCode().value() == 404) {
      return new EntitlementIntegrationException(
          EntitlementIntegrationException.Reason.NOT_FOUND, "entitlement user not found", ex);
    }
    if (ex.getStatusCode().is5xxServerError()) {
      return new EntitlementIntegrationException(
          EntitlementIntegrationException.Reason.BAD_GATEWAY, "entitlement server error", ex);
    }
    return new EntitlementIntegrationException(
        EntitlementIntegrationException.Reason.BAD_GATEWAY, "entitlement request failed", ex);
  }

  private EntitlementIntegrationException mapResourceException(ResourceAccessException ex) {
    if (isTimeout(ex)) {
      logger.warn("entitlement getUserEntitlements timed out");
      return new EntitlementIntegrationException(
          EntitlementIntegrationException.Reason.TIMEOUT, "entitlement request timeout", ex);
    }
    logger.warn("entitlement getUserEntitlements connection failed", ex);
    return new EntitlementIntegrationException(
        EntitlementIntegrationException.Reason.BAD_GATEWAY, "entitlement connection failed", ex);
  }

  private void validateUserId(String userId) {
    if (isBlank(userId)) {
      throw new IllegalArgumentException("userId is required");
    }
  }

  private boolean isTimeout(ResourceAccessException ex) {
    Throwable current = ex;
    while (current != null) {
      if (current instanceof SocketTimeoutException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
