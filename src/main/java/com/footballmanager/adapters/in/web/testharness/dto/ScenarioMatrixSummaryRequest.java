package com.footballmanager.adapters.in.web.testharness.dto;

public record ScenarioMatrixSummaryRequest(
    Long seedStart,
    Integer seedCount,
    String scenarioGroup,
    String controlledTeamSide
) {}
