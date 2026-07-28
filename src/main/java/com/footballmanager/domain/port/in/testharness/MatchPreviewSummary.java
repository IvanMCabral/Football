package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.List;
import java.util.Map;

public record MatchPreviewSummary(
    String matchId,
    String controlledTeamSide,
    long seedStart,
    long seedEnd,
    int seedCount,
    String teamName,
    String formation,
    double avgGoalsFor,
    double avgGoalsAgainst,
    double avgGoalDiff,
    double avgPossessionFor,
    double avgShotsFor,
    double avgShotsAgainst,
    double avgShotDiff,
    double avgXgFor,
    double avgXgAgainst,
    double avgXgDiff,
    double avgCentralShotsFor,
    double avgWideShotsFor,
    double avgLongShotsFor,
    double avgCentralShotsAgainst,
    double avgWideShotsAgainst,
    double avgLongShotsAgainst
) {}
