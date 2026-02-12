package com.example.account.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.account.model.AccountStatus;
import com.example.account.model.AuditLogRecord;
import com.example.account.model.UserRecord;
import com.example.account.repository.AuditLogRepository;
import com.example.account.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private AuditLogRepository auditLogRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AdminUserService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminUserService(
            userRepository,
            auditLogRepository,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
            objectMapper);
  }

  @Test
  void suspendRejectsBlankActor() {
    assertThatThrownBy(() -> service.suspendUser("", "user-1", "abuse"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void suspendUpdatesStatusAndWritesAuditLog() throws Exception {
    when(userRepository.updateStatus(eq("user-1"), eq("SUSPENDED")))
        .thenReturn(
            Optional.of(
                new UserRecord(
                    "user-1", "n", "ja", AccountStatus.SUSPENDED, Instant.now(), Instant.now())));

    service.suspendUser("admin-1", "user-1", "abuse");

    verify(userRepository).updateStatus("user-1", "SUSPENDED");
    final ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
    verify(auditLogRepository).insert(captor.capture());
    final JsonNode metadata = objectMapper.readTree(captor.getValue().metadataJson());
    org.assertj.core.api.Assertions.assertThat(metadata.get("reason").asText()).isEqualTo("abuse");
  }

  @Test
  void suspendEscapesReasonForAuditMetadataJson() throws Exception {
    when(userRepository.updateStatus(eq("user-1"), eq("SUSPENDED")))
        .thenReturn(
            Optional.of(
                new UserRecord(
                    "user-1", "n", "ja", AccountStatus.SUSPENDED, Instant.now(), Instant.now())));

    service.suspendUser("admin-1", "user-1", "bad \"input\"");

    final ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
    verify(auditLogRepository).insert(captor.capture());
    final JsonNode metadata = objectMapper.readTree(captor.getValue().metadataJson());
    org.assertj.core.api.Assertions.assertThat(metadata.get("reason").asText())
        .isEqualTo("bad \"input\"");
  }
}
