package com.footballmanager.adapters.in.web.auth.dto;

public record JwtTokenResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String tokenType
) {}
