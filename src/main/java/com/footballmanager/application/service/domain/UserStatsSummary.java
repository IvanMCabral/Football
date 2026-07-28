package com.footballmanager.application.service.domain;

public record UserStatsSummary(
    String userName,
    int matchesPlayed,
    int wins,
    int losses,
    double winPercentage
) {
}
