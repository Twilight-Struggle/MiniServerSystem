package com.example.gateway_bff.model;

import java.time.Instant;

public record OidcLoginContext(String state, String nonce, Instant issuedAt, Instant expiresAt) {}
