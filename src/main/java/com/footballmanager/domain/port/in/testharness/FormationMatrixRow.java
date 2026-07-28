package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.List;
import java.util.Map;

public record FormationMatrixRow(
    String formation,
    int homeGoals,
    int awayGoals,
    double homeXg,
    double awayXg,
    int homeShots,
    int awayShots,
    int homePossession,
    int awayPossession,
    int homeCentralShots,
    int homeWideShots,
    int homeLongShots,
    int awayCentralShots,
    int awayWideShots,
    int awayLongShots,
    int homeLeftWideShots,
    int homeRightWideShots,
    double homeLeftWideXg,
    double homeRightWideXg,
    int awayLeftWideShots,
    int awayRightWideShots,
    double awayLeftWideXg,
    double awayRightWideXg,
    double shapePossessionMultiplier,
    double shapeAttackVolumeMultiplier,
    double shapeDefensiveResistanceMultiplier,
    double shapeAttackLeft,
    double shapeAttackCenter,
    double shapeAttackRight,
    double shapeDefenseLeft,
    double shapeDefenseCenter,
    double shapeDefenseRight
) {}
