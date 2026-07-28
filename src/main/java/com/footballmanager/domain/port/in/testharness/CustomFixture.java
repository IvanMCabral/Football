package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.List;
import java.util.Map;

public record CustomFixture(String homeTeamId, String awayTeamId, int round, String matchId) {
    public CustomFixture {
        if (homeTeamId == null || homeTeamId.isBlank()) {
            throw new IllegalArgumentException("homeTeamId is required");
        }
        if (awayTeamId == null || awayTeamId.isBlank()) {
            throw new IllegalArgumentException("awayTeamId is required");
        }
        if (homeTeamId.equals(awayTeamId)) {
            throw new IllegalArgumentException(
                "homeTeamId and awayTeamId must differ (got '" + homeTeamId + "')");
        }
        if (round < 1) {
            throw new IllegalArgumentException("round must be >= 1 (got " + round + ")");
        }
    }
}
