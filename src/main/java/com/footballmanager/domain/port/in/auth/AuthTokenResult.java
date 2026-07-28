package com.footballmanager.domain.port.in.auth;

public record AuthTokenResult(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String tokenType
) {}
