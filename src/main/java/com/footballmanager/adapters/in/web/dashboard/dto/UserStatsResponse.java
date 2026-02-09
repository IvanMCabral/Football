package com.footballmanager.adapters.in.web.dashboard.dto;

public record UserStatsResponse(
    String userName,
    int matchesPlayed,
    int matchesWon,
    int matchesLost,
    double winPercentage
) {}
