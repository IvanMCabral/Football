package com.footballmanager.adapters.in.web.game.dto;

import java.util.UUID;

/**
 * Entrada de la tabla de posiciones
 */
public record StandingDTO(
    UUID teamId,
    String teamName,
    int played,
    int wins,
    int draws,
    int losses,
    int goalsFor,
    int goalsAgainst,
    int goalDifference,
    int points
) {}
