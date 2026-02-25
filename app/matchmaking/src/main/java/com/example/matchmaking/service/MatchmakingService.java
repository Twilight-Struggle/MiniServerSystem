package com.example.matchmaking.service;

import com.example.matchmaking.api.InvalidMatchmakingRequestException;
import com.example.matchmaking.api.TicketAccessDeniedException;
import com.example.matchmaking.api.TicketNotFoundException;
import com.example.matchmaking.api.request.JoinMatchmakingTicketRequest;
import com.example.matchmaking.api.response.CancelMatchmakingTicketResponse;
import com.example.matchmaking.api.response.JoinMatchmakingTicketResponse;
import com.example.matchmaking.api.response.MatchedTicketPayload;
import com.example.matchmaking.api.response.TicketStatusResponse;
import com.example.matchmaking.config.MatchmakingProperties;
import com.example.matchmaking.model.MatchMode;
import com.example.matchmaking.model.TicketRecord;
import com.example.matchmaking.model.TicketStatus;
import com.example.matchmaking.repository.MatchmakingTicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MatchmakingService {

  private final MatchmakingTicketRepository ticketRepository;
  private final MatchmakingProperties properties;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "ObjectMapper は Spring 管理の共有コンポーネントで防御的コピーが不可能なため")
  private final ObjectMapper objectMapper;

  public MatchmakingService(
      MatchmakingTicketRepository ticketRepository,
      MatchmakingProperties properties,
      ObjectMapper objectMapper) {
    this.ticketRepository = ticketRepository;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public JoinMatchmakingTicketResponse join(
      String mode, String userId, JoinMatchmakingTicketRequest request) {
    final MatchMode matchMode = validateJoinRequest(mode, userId, request);
    final String attributesJson = serializeAttributes(request.attributes());
    final TicketRecord record =
        ticketRepository.createOrReuseTicket(
            matchMode,
            userId,
            request.idempotencyKey(),
            attributesJson,
            properties.ticketTtl(),
            properties.idempotencyTtl());
    return new JoinMatchmakingTicketResponse(
        record.ticketId(), record.status().name(), toIsoOrNull(record.expiresAt()));
  }

  public TicketStatusResponse getTicketStatus(String ticketId, String userId) {
    validateTicketIdAndUserId(ticketId, userId);
    final TicketRecord record = requireOwnedTicket(ticketId, userId);
    return toStatusResponse(record);
  }

  public CancelMatchmakingTicketResponse cancelTicket(String ticketId, String userId) {
    validateTicketIdAndUserId(ticketId, userId);
    requireOwnedTicket(ticketId, userId);
    final TicketRecord cancelled =
        ticketRepository
            .cancelTicket(ticketId)
            .orElseThrow(() -> new TicketNotFoundException(ticketId));
    return new CancelMatchmakingTicketResponse(cancelled.ticketId(), cancelled.status().name());
  }

  private MatchMode validateJoinRequest(
      String mode, String userId, JoinMatchmakingTicketRequest request) {
    if (mode == null || mode.isBlank()) {
      throw new InvalidMatchmakingRequestException("mode is required");
    }
    final MatchMode matchMode = parseMode(mode);
    if (userId == null || userId.isBlank()) {
      throw new InvalidMatchmakingRequestException("userId is required");
    }
    if (request == null) {
      throw new InvalidMatchmakingRequestException("request is required");
    }
    if (request.partySize() == null || request.partySize() != 1) {
      throw new InvalidMatchmakingRequestException("party_size must be 1");
    }
    if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
      throw new InvalidMatchmakingRequestException("idempotency_key is required");
    }
    return matchMode;
  }

  private MatchMode parseMode(String mode) {
    try {
      return MatchMode.fromValue(mode);
    } catch (IllegalArgumentException ex) {
      throw new InvalidMatchmakingRequestException(ex.getMessage());
    }
  }

  private void validateTicketIdAndUserId(String ticketId, String userId) {
    if (ticketId == null || ticketId.isBlank()) {
      throw new InvalidMatchmakingRequestException("ticketId is required");
    }
    if (userId == null || userId.isBlank()) {
      throw new InvalidMatchmakingRequestException("userId is required");
    }
  }

  private TicketRecord requireOwnedTicket(String ticketId, String userId) {
    final TicketRecord record =
        ticketRepository
            .findTicketById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException(ticketId));
    ensureOwner(record, userId);
    return record;
  }

  private void ensureOwner(TicketRecord record, String userId) {
    if (!userId.equals(record.userId())) {
      throw new TicketAccessDeniedException(record.ticketId());
    }
  }

  private TicketStatusResponse toStatusResponse(TicketRecord record) {
    final MatchedTicketPayload matched =
        record.status() == TicketStatus.MATCHED
                && record.matchId() != null
                && !record.matchId().isBlank()
            ? new MatchedTicketPayload(record.matchId(), List.of(), Map.of())
            : null;
    return new TicketStatusResponse(
        record.ticketId(), record.status().name(), toIsoOrNull(record.expiresAt()), matched);
  }

  private String serializeAttributes(Map<String, Object> attributes) {
    final Map<String, Object> safeAttributes = attributes == null ? Map.of() : attributes;
    try {
      return objectMapper.writeValueAsString(safeAttributes);
    } catch (JsonProcessingException ex) {
      throw new InvalidMatchmakingRequestException("attributes serialization failed");
    }
  }

  private String toIsoOrNull(java.time.Instant instant) {
    return instant == null ? null : instant.toString();
  }
}
