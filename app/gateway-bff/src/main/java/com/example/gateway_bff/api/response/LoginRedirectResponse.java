package com.example.gateway_bff.api.response;

public record LoginRedirectResponse(String authorizationUrl, String state) {}
