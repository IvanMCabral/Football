package com.footballmanager.adapters.in.web.game.dto;

/**
 * Estado del torneo: ronda actual, rondas totales, si está finalizado
 */
public record TournamentStatusDTO(
    int currentRound,
    int totalRounds,
    boolean hasNextRound,
    boolean isFinished,
    ChampionDTO champion
) {}
