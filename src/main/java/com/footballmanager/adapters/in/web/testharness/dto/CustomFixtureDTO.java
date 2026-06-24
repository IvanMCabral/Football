package com.footballmanager.adapters.in.web.testharness.dto;

import java.util.UUID;

/**
 * V24D20-TESTHARNESS — request body for {@code POST /api/v1/test-harness/career/replace-fixtures}.
 *
 * <p>If {@link #matchId} is null, the harness generates a fresh UUID.
 * The {@link #homeTeamId}/{@link #awayTeamId} pair must reference
 * existing {@code sessionTeamId}s of the user's career.
 */
public record CustomFixtureDTO(
    String homeTeamId,
    String awayTeamId,
    int round,
    UUID matchId
) {}
