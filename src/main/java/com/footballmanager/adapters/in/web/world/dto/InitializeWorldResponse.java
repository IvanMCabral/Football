package com.footballmanager.adapters.in.web.world.dto;

import java.util.UUID;

/**
 * Response de inicialización de WorldSnapshot
 */
public record InitializeWorldResponse(
    UUID userId,
    int leaguesCount,
    int teamsCount,
    int playersCount,
    long createdAt,
    String message
) {}
