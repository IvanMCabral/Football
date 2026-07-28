package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.List;
import java.util.Map;

public record LineupDiagnosticPlayer(
    String playerId,
    String name,
    String naturalPosition,
    String tacticalPosition,
    String slotRole,
    String slotSide,
    String slotId,
    Double xPercent,
    Double yPercent,
    String positionSource,
    String curatedRoles,
    String preferredSides,
    int roleBonus,
    int sideBonus,
    int assignmentScore,
    String assignmentVerdict,
    String assignmentRead,
    int attack,
    int defense,
    int technique,
    int speed,
    int stamina,
    int mentality,
    int overall,
    double effectiveness,
    double collective
) {}
