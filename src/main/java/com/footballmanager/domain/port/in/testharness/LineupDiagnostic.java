package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.List;
import java.util.Map;

public record LineupDiagnostic(
    String matchId,
    long seed,
    LineupDiagnosticTeam home,
    LineupDiagnosticTeam away
) {}
