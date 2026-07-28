package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.List;
import java.util.Map;

public record LineupDiagnosticTeam(
    String teamId,
    String teamName,
    String formation,
    TeamStyle style,
    double avgOverall,
    double avgCollective,
    double avgEffectiveness,
    int starters,
    LineupWidthDiagnostic width,
    List<LineupDiagnosticPlayer> players
) {}
