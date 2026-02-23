package com.example.gateway_bff.service;

import com.example.gateway_bff.config.AccountClientProperties;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import com.example.gateway_bff.service.dto.AccountIdentityResolveRequest;
import com.example.gateway_bff.service.dto.AccountIdentityResolveResponse;
import java.net.SocketTimeoutException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class AccountResolveClient {

  private static final Logger logger = LoggerFactory.getLogger(AccountResolveClient.class);

  private final RestClient accountRestClient;
  private final AccountClientProperties properties;

  public AuthenticatedUser resolveIdentity(@NonNull OidcClaims claims) {
    if (isBlank(claims.provider())) {
      throw new IllegalArgumentException("provider is required");
    }
    if (isBlank(claims.subject())) {
      throw new IllegalArgumentException("subject is required");
    }

    final AccountIdentityResolveRequest request = toResolveRequest(claims);
    final AccountIdentityResolveResponse response = callResolveIdentity(request);
    return toAuthenticatedUser(response);
  }

  private AccountIdentityResolveRequest toResolveRequest(OidcClaims claims) {
    return new AccountIdentityResolveRequest(
        claims.provider(),
        claims.subject(),
        claims.email(),
        claims.emailVerified(),
        claims.name(),
        claims.picture());
  }

  private AccountIdentityResolveResponse callResolveIdentity(
      AccountIdentityResolveRequest request) {
    try {
      final AccountIdentityResolveResponse response =
          accountRestClient
              .post()
              .uri(properties.resolveIdentityPath())
              .header(properties.internalApiHeaderName(), properties.internalApiToken())
              .body(request)
              .retrieve()
              .body(AccountIdentityResolveResponse.class);
      if (response == null) {
        logger.warn("account resolveIdentity returned empty body");
        throw new AccountIntegrationException(
            AccountIntegrationException.Reason.INVALID_RESPONSE, "account response is empty");
      }
      return response;
    } catch (RestClientResponseException ex) {
      logger.warn(
          "account resolveIdentity failed with http status={} statusText={}",
          ex.getStatusCode().value(),
          ex.getStatusText());
      if (ex.getStatusCode().value() == 401) {
        throw new AccountIntegrationException(
            AccountIntegrationException.Reason.UNAUTHORIZED, "account rejected internal auth", ex);
      }
      if (ex.getStatusCode().value() == 403) {
        throw new AccountIntegrationException(
            AccountIntegrationException.Reason.FORBIDDEN, "account denied access", ex);
      }
      if (ex.getStatusCode().is5xxServerError()) {
        throw new AccountIntegrationException(
            AccountIntegrationException.Reason.BAD_GATEWAY, "account server error", ex);
      }
      throw new AccountIntegrationException(
          AccountIntegrationException.Reason.BAD_GATEWAY, "account request failed", ex);
    } catch (ResourceAccessException ex) {
      if (isTimeout(ex)) {
        logger.warn("account resolveIdentity timed out");
        throw new AccountIntegrationException(
            AccountIntegrationException.Reason.TIMEOUT, "account request timeout", ex);
      }
      logger.warn("account resolveIdentity connection failed", ex);
      throw new AccountIntegrationException(
          AccountIntegrationException.Reason.BAD_GATEWAY, "account connection failed", ex);
    } catch (AccountIntegrationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      logger.warn("account resolveIdentity response parse failed", ex);
      throw new AccountIntegrationException(
          AccountIntegrationException.Reason.INVALID_RESPONSE, "account response parse failed", ex);
    }
  }

  private AuthenticatedUser toAuthenticatedUser(AccountIdentityResolveResponse response) {
    if (response == null || isBlank(response.userId()) || isBlank(response.accountStatus())) {
      logger.warn("account resolveIdentity response validation failed");
      throw new AccountIntegrationException(
          AccountIntegrationException.Reason.INVALID_RESPONSE, "account response is invalid");
    }
    return new AuthenticatedUser(response.userId(), response.accountStatus(), response.roles());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
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
}
