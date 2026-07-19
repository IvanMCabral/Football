package com.footballmanager.adapters.in.web.testharness.dto;

import java.util.List;

public record RoleSlotImpactRequest(
    String slotId,
    List<String> naturalPositions,
    Long seedStart,
    Integer seedCount,
    String controlledTeamSide
) {}
