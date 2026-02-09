package com.footballmanager.adapters.in.web.game.dto;

import java.util.UUID;

/**
 * Campeón del torneo
 */
public record ChampionDTO(
    UUID teamId,
    String teamName,
    int points,
    int wins,
    int goalDifference
) {}
