package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.List;
import java.util.Map;

public record ScenarioMatrixRow(
    String scenario,
    String description,
    String formation,
    TeamStyle initialStyle,
    Integer changeMinute,
    TeamStyle changedStyle,
    String actionType,
    String actionDetail,
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
    double homeCentralXg,
    double homeWideXg,
    double homeLongXg,
    int homeLeftWideShots,
    int homeRightWideShots,
    double homeLeftWideXg,
    double homeRightWideXg,
    double awayCentralXg,
    double awayWideXg,
    double awayLongXg,
    int awayLeftWideShots,
    int awayRightWideShots,
    double awayLeftWideXg,
    double awayRightWideXg,
    long tacticalChanges,
    long substitutions
) {}
