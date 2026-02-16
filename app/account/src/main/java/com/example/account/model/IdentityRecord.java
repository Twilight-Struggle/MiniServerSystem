package com.example.account.model;

import java.time.Instant;

public record IdentityRecord(
    String provider,
    String subject,
    String userId,
    String email,
    boolean emailVerified,
    Instant createdAt) {}
