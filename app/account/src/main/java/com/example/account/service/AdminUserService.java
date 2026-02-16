package com.example.account.service;

import com.example.account.model.AuditLogRecord;
import com.example.account.repository.AuditLogRepository;
import com.example.account.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

  private final UserRepository userRepository;
  private final AuditLogRepository auditLogRepository;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  @Transactional
  public void suspendUser(String actorUserId, String targetUserId, String reason) {
    if (actorUserId == null || actorUserId.isBlank()) {
      throw new IllegalArgumentException("actor_user_id is required");
    }
    if (targetUserId == null || targetUserId.isBlank()) {
      throw new IllegalArgumentException("target_user_id is required");
    }

    userRepository
        .updateStatus(targetUserId, "SUSPENDED")
        .orElseThrow(() -> new IllegalArgumentException("user not found"));

    final String metadata = createMetadataJson(reason);
    final AuditLogRecord audit =
        new AuditLogRecord(
            UUID.randomUUID().toString(),
            actorUserId,
            "SUSPEND_USER",
            targetUserId,
            metadata,
            Instant.now(clock));
    auditLogRepository.insert(audit);
  }

  private String createMetadataJson(String reason) {
    try {
      return objectMapper.writeValueAsString(Map.of("reason", reason == null ? "" : reason));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize audit metadata", e);
    }
  }
}
