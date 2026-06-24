package com.footballmanager.adapters.in.web.testharness.dto;

/**
 * V24D24.3-HOTFIX: Body for {@code POST /api/v1/test-harness/career/reset-round}.
 *
 * <p>The frontend hydrates {@code roundId} from
 * {@code /api/v1/career/fixtures/round-with-bye} (the same value carried
 * in {@code TestHarnessMatchRow.roundId}) and POSTs it here RIGHT BEFORE
 * {@code /match-engine/rounds/start} so the {@code "Simulate round"}
 * button is idempotent — re-running the same round with the same input
 * produces a fresh simulation rather than re-reading the cached result.
 *
 * <p>{@code roundId} is a deterministic UUID derived from
 * {@code (careerId, round)} via
 * {@code FixtureQueryHelper.deriveRoundId(careerId, round)}; the backend
 * recovers the round number by enumerating the 1..totalRounds range
 * and matching the UUID. With {@code totalRounds <= 38} (typical
 * tournament) this is cheap.
 */
public record ResetRoundRequest(String roundId) {
    public ResetRoundRequest {
        if (roundId == null || roundId.isBlank()) {
            throw new IllegalArgumentException("roundId is required and must be a non-blank UUID string");
        }
    }
}
