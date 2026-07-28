package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.List;
import java.util.Map;

public record RoleSlotImpactSummaryRow(
    String matchId,
    String formation,
    String slotId,
    double slotXPercent,
    double slotYPercent,
    String baselinePlayerId,
    String baselinePlayerName,
    String baselineNaturalPosition,
    String testedNaturalPosition,
    String tacticalPosition,
    long seedStart,
    long seedEnd,
    int seedCount,
    double playerEffectiveness,
    double playerCollective,
    double avgGoalsFor,
    double avgGoalsAgainst,
    double avgGoalDiff,
    double avgShotsFor,
    double avgShotsAgainst,
    double avgPossessionFor,
    double avgXgFor,
    double avgXgAgainst,
    double avgXgDiff,
    double avgCentralShotsFor,
    double avgWideShotsFor,
    double avgLongShotsFor,
    double avgCentralXgFor,
    double avgWideXgFor,
    double avgLongXgFor
) {}
