package com.example.gateway_bff.service;

import com.example.gateway_bff.api.request.UserPatchRequest;
import com.example.gateway_bff.api.response.UserResponse;
import com.example.gateway_bff.config.AccountClientProperties;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.service.dto.AccountUserPatchRequest;
import com.example.gateway_bff.service.dto.AccountUserResponse;
import java.net.SocketTimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class AccountUserClient {

  private final RestClient accountRestClient;
  private final AccountClientProperties properties;

  public UserResponse getUser(String targetUserId, AuthenticatedUser requester) {
    validateTargetUserId(targetUserId);
    validateRequester(requester);
    final AccountUserResponse response =
        callGetUser(targetUserId, requester.userId(), serializeRoles(requester));
    return toUserResponse(response);
  }

  public UserResponse patchUser(
      String targetUserId, UserPatchRequest patchRequest, AuthenticatedUser requester) {
    validateTargetUserId(targetUserId);
    validateRequester(requester);
    if (patchRequest == null) {
      throw new IllegalArgumentException("patchRequest is required");
    }
    final AccountUserResponse response =
        callPatchUser(
            targetUserId,
            requester.userId(),
            serializeRoles(requester),
            new AccountUserPatchRequest(patchRequest.displayName(), patchRequest.locale()));
    return toUserResponse(response);
  }

  private void validateTargetUserId(String targetUserId) {
    if (targetUserId == null || targetUserId.isBlank()) {
      throw new IllegalArgumentException("targetUserId is required");
    }
  }

  private void validateRequester(AuthenticatedUser requester) {
    if (requester == null || requester.userId() == null || requester.userId().isBlank()) {
      throw new IllegalArgumentException("requester userId is required");
    }
  }

  private AccountUserResponse callGetUser(
      String targetUserId, String requesterUserId, String requesterRoles) {
    try {
      final RestClient.RequestHeadersSpec<?> spec =
          accountRestClient
              .get()
              .uri(properties.getUserPath(), targetUserId)
              .header(properties.internalApiHeaderName(), properties.internalApiToken())
              .header(properties.userIdHeaderName(), requesterUserId);
      withRolesHeader(spec, requesterRoles);
      return requireValid(spec.retrieve().body(AccountUserResponse.class));
    } catch (RestClientResponseException ex) {
      throw mapResponseException(ex);
    } catch (ResourceAccessException ex) {
      throw mapResourceException(ex);
    } catch (AccountIntegrationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new AccountIntegrationException(
          AccountIntegrationException.Reason.INVALID_RESPONSE, "account response parse failed", ex);
    }
  }

  private AccountUserResponse callPatchUser(
      String targetUserId,
      String requesterUserId,
      String requesterRoles,
      AccountUserPatchRequest request) {
    try {
      final RestClient.RequestBodySpec spec =
          accountRestClient
              .patch()
              .uri(properties.patchUserPath(), targetUserId)
              .header(properties.internalApiHeaderName(), properties.internalApiToken())
              .header(properties.userIdHeaderName(), requesterUserId);
      withRolesHeader(spec, requesterRoles);
      return requireValid(spec.body(request).retrieve().body(AccountUserResponse.class));
    } catch (RestClientResponseException ex) {
      throw mapResponseException(ex);
    } catch (ResourceAccessException ex) {
      throw mapResourceException(ex);
    } catch (AccountIntegrationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new AccountIntegrationException(
          AccountIntegrationException.Reason.INVALID_RESPONSE, "account response parse failed", ex);
    }
  }

  private void withRolesHeader(RestClient.RequestHeadersSpec<?> spec, String roles) {
    if (roles != null && !roles.isBlank()) {
      spec.header(properties.userRolesHeaderName(), roles);
    }
  }

  private String serializeRoles(AuthenticatedUser requester) {
    if (requester.roles() == null || requester.roles().isEmpty()) {
      return "";
    }
    return String.join(",", requester.roles());
  }

  private AccountUserResponse requireValid(AccountUserResponse response) {
    if (response == null || isBlank(response.userId()) || isBlank(response.status())) {
      throw new AccountIntegrationException(
          AccountIntegrationException.Reason.INVALID_RESPONSE, "account response is invalid");
    }
    return response;
  }

  private UserResponse toUserResponse(AccountUserResponse response) {
    return new UserResponse(
        response.userId(),
        response.displayName(),
        response.locale(),
        response.status(),
        response.roles());
  }

  private AccountIntegrationException mapResponseException(RestClientResponseException ex) {
    if (ex.getStatusCode().value() == 401) {
      return new AccountIntegrationException(
          AccountIntegrationException.Reason.UNAUTHORIZED, "account rejected internal auth", ex);
    }
    if (ex.getStatusCode().value() == 403) {
      return new AccountIntegrationException(
          AccountIntegrationException.Reason.FORBIDDEN, "account denied access", ex);
    }
    if (ex.getStatusCode().value() == 404) {
      return new AccountIntegrationException(
          AccountIntegrationException.Reason.NOT_FOUND, "account user not found", ex);
    }
    if (ex.getStatusCode().is5xxServerError()) {
      return new AccountIntegrationException(
          AccountIntegrationException.Reason.BAD_GATEWAY, "account server error", ex);
    }
    return new AccountIntegrationException(
        AccountIntegrationException.Reason.BAD_GATEWAY, "account request failed", ex);
  }

  private AccountIntegrationException mapResourceException(ResourceAccessException ex) {
    if (isTimeout(ex)) {
      return new AccountIntegrationException(
          AccountIntegrationException.Reason.TIMEOUT, "account request timeout", ex);
    }
    return new AccountIntegrationException(
        AccountIntegrationException.Reason.BAD_GATEWAY, "account connection failed", ex);
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
