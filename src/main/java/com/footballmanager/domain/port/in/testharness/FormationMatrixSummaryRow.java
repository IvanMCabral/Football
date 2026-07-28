package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.List;
import java.util.Map;

public record FormationMatrixSummaryRow(
    String formation,
    long seedStart,
    long seedEnd,
    int seedCount,
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
    double avgLongShotsAgainst,
    double avgLeftWideShotsFor,
    double avgRightWideShotsFor,
    double avgLeftWideShotsAgainst,
    double avgRightWideShotsAgainst,
    double avgLeftWideXgFor,
    double avgRightWideXgFor,
    double avgLeftWideXgAgainst,
    double avgRightWideXgAgainst,
    double avgShapePossessionMultiplier,
    double avgShapeAttackVolumeMultiplier,
    double avgShapeDefensiveResistanceMultiplier,
    double avgShapeAttackLeft,
    double avgShapeAttackCenter,
    double avgShapeAttackRight,
    double avgShapeDefenseLeft,
    double avgShapeDefenseCenter,
    double avgShapeDefenseRight
) {}
