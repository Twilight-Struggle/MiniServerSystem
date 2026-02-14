package com.example.account.api.request;

public record IdentityResolveRequest(
    String provider,
    String subject,
    String email,
    Boolean emailVerified,
    String name,
    String picture) {}
