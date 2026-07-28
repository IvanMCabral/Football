package com.footballmanager.domain.port.in.game;

public record TournamentStatus(
    int currentRound,
    int totalRounds,
    boolean hasNextRound,
    boolean isFinished,
    TournamentChampion champion
) {}
