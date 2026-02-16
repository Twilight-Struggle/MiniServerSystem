package com.example.account.model;

import java.time.Instant;

public record AuditLogRecord(
    String id,
    String actorUserId,
    String action,
    String targetUserId,
    String metadataJson,
    Instant createdAt) {}
