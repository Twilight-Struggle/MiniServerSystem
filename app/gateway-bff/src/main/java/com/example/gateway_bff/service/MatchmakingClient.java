package com.example.gateway_bff.service;

import com.example.gateway_bff.config.MatchmakingClientProperties;
import com.example.gateway_bff.service.dto.MatchmakingCancelTicketResponse;
import com.example.gateway_bff.service.dto.MatchmakingJoinTicketRequest;
import com.example.gateway_bff.service.dto.MatchmakingJoinTicketResponse;
import com.example.gateway_bff.service.dto.MatchmakingTicketStatusResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class MatchmakingClient {

  private static final Logger logger = LoggerFactory.getLogger(MatchmakingClient.class);

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "RestClient は Spring 管理の共有コンポーネントで防御的コピーが不可能なため")
  private final RestClient matchmakingRestClient;

  private final MatchmakingClientProperties properties;

  public MatchmakingClient(
      RestClient matchmakingRestClient, MatchmakingClientProperties properties) {
    this.matchmakingRestClient = matchmakingRestClient;
    this.properties = properties;
  }

  public MatchmakingJoinTicketResponse joinTicket(
      String mode, String userId, MatchmakingJoinTicketRequest request) {
    validateMode(mode);
    validateUserId(userId);
    try {
      return requireJoinResponse(
          matchmakingRestClient
              .post()
              .uri(properties.joinTicketPath(), mode)
              .header(properties.userIdHeaderName(), userId)
              .body(request)
              .retrieve()
              .body(MatchmakingJoinTicketResponse.class));
    } catch (RestClientResponseException ex) {
      throw mapResponseException(ex, "joinTicket");
    } catch (ResourceAccessException ex) {
      throw mapResourceException(ex, "joinTicket");
    } catch (MatchmakingIntegrationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      logger.warn("matchmaking joinTicket response parse failed", ex);
      throw new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.INVALID_RESPONSE,
          "matchmaking response parse failed",
          ex);
    }
  }

  public MatchmakingTicketStatusResponse getTicketStatus(String ticketId, String userId) {
    validateTicketId(ticketId);
    validateUserId(userId);
    try {
      return requireStatusResponse(
          matchmakingRestClient
              .get()
              .uri(properties.getTicketPath(), ticketId)
              .header(properties.userIdHeaderName(), userId)
              .retrieve()
              .body(MatchmakingTicketStatusResponse.class));
    } catch (RestClientResponseException ex) {
      throw mapResponseException(ex, "getTicketStatus");
    } catch (ResourceAccessException ex) {
      throw mapResourceException(ex, "getTicketStatus");
    } catch (MatchmakingIntegrationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      logger.warn("matchmaking getTicketStatus response parse failed", ex);
      throw new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.INVALID_RESPONSE,
          "matchmaking response parse failed",
          ex);
    }
  }

  public MatchmakingCancelTicketResponse cancelTicket(String ticketId, String userId) {
    validateTicketId(ticketId);
    validateUserId(userId);
    try {
      return requireCancelResponse(
          matchmakingRestClient
              .delete()
              .uri(properties.cancelTicketPath(), ticketId)
              .header(properties.userIdHeaderName(), userId)
              .retrieve()
              .body(MatchmakingCancelTicketResponse.class));
    } catch (RestClientResponseException ex) {
      throw mapResponseException(ex, "cancelTicket");
    } catch (ResourceAccessException ex) {
      throw mapResourceException(ex, "cancelTicket");
    } catch (MatchmakingIntegrationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      logger.warn("matchmaking cancelTicket response parse failed", ex);
      throw new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.INVALID_RESPONSE,
          "matchmaking response parse failed",
          ex);
    }
  }

  private MatchmakingJoinTicketResponse requireJoinResponse(
      MatchmakingJoinTicketResponse response) {
    if (response == null || isBlank(response.ticketId()) || isBlank(response.status())) {
      throw new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.INVALID_RESPONSE,
          "matchmaking response is invalid");
    }
    return response;
  }

  private MatchmakingTicketStatusResponse requireStatusResponse(
      MatchmakingTicketStatusResponse response) {
    if (response == null || isBlank(response.ticketId()) || isBlank(response.status())) {
      throw new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.INVALID_RESPONSE,
          "matchmaking response is invalid");
    }
    return response;
  }

  private MatchmakingCancelTicketResponse requireCancelResponse(
      MatchmakingCancelTicketResponse response) {
    if (response == null || isBlank(response.ticketId()) || isBlank(response.status())) {
      throw new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.INVALID_RESPONSE,
          "matchmaking response is invalid");
    }
    return response;
  }

  private MatchmakingIntegrationException mapResponseException(
      RestClientResponseException ex, String operation) {
    logger.warn(
        "matchmaking {} failed with http status={} statusText={}",
        operation,
        ex.getStatusCode().value(),
        ex.getStatusText());
    if (ex.getStatusCode().value() == 403) {
      return new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.FORBIDDEN, "matchmaking denied access", ex);
    }
    if (ex.getStatusCode().value() == 404) {
      return new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.NOT_FOUND, "matchmaking ticket not found", ex);
    }
    if (ex.getStatusCode().is5xxServerError()) {
      return new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.BAD_GATEWAY, "matchmaking server error", ex);
    }
    return new MatchmakingIntegrationException(
        MatchmakingIntegrationException.Reason.BAD_GATEWAY, "matchmaking request failed", ex);
  }

  private MatchmakingIntegrationException mapResourceException(
      ResourceAccessException ex, String operation) {
    if (isTimeout(ex)) {
      logger.warn("matchmaking {} timed out", operation);
      return new MatchmakingIntegrationException(
          MatchmakingIntegrationException.Reason.TIMEOUT, "matchmaking request timeout", ex);
    }
    logger.warn("matchmaking {} connection failed", operation, ex);
    return new MatchmakingIntegrationException(
        MatchmakingIntegrationException.Reason.BAD_GATEWAY, "matchmaking connection failed", ex);
  }

  private void validateMode(String mode) {
    if (isBlank(mode)) {
      throw new IllegalArgumentException("mode is required");
    }
  }

  private void validateTicketId(String ticketId) {
    if (isBlank(ticketId)) {
      throw new IllegalArgumentException("ticketId is required");
    }
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
