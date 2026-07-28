package com.footballmanager.domain.port.in.game;

import java.util.UUID;

public record TournamentStanding(
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
