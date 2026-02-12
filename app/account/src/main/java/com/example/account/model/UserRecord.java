package com.example.account.model;

import java.time.Instant;

public record UserRecord(
    String userId,
    String displayName,
    String locale,
    AccountStatus status,
    Instant createdAt,
    Instant updatedAt) {}
