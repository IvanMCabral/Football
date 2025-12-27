package com.footballmanager.application.dto;

public record JwtTokenResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String tokenType
) {}
