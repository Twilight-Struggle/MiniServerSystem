package com.example.gateway_bff.model;

public record OidcClaims(
    String provider,
    String subject,
    String email,
    boolean emailVerified,
    String name,
    String picture,
    String issuer,
    String audience,
    long expiresAtEpochSeconds,
    String nonce) {}
