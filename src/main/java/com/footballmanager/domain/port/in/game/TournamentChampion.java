package com.footballmanager.domain.port.in.game;

import java.util.UUID;

public record TournamentChampion(
    UUID teamId,
    String teamName,
    int points,
    int wins,
    int goalDifference
) {}
