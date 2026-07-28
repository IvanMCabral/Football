package com.footballmanager.domain.port.in.auth;

public record AuthUserInfo(
    String id,
    String email,
    String username,
    String displayName,
    String teamId,
    String teamName
) {}
