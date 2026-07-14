package com.footballmanager.adapters.in.web.testharness.dto;

public record PositionPixelMatrixSummaryRequest(
    String playerId,
    Double targetXPercent,
    Double targetYPercent,
    Long seedStart,
    Integer seedCount
) {}
