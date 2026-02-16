package com.example.gateway_bff.service.dto;

public record AccountIdentityResolveRequest(
    String provider,
    String subject,
    String email,
    Boolean emailVerified,
    String name,
    String picture) {}
