package com.footballmanager.application.service.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D24.2: Unit coverage for {@link FixtureQueryHelper#deriveRoundId(String, int)}.
 *
 * <p>The front test-harness UI needs a deterministic {@code roundId} per
 * {@code (careerId, round)} pair to POST against
 * {@code /api/v1/match-engine/rounds/start} without first having to register a
 * live engine. This is the contract:
 *
 * <ul>
 *   <li>deterministic: same inputs → same UUID, byte-exact</li>
 *   <li>different rounds → different roundIds (round number is part of the hash)</li>
 *   <li>different careers → different roundIds (careerId is part of the hash)</li>
 *   <li>returns a valid UUID string (front parses it as UUID before posting)</li>
 *   <li>null careerId → null (graceful degradation; happens when MatchFixture
 *       is queried outside a career context, e.g. ad-hoc helper callers)</li>
 * </ul>
 */
@DisplayName("FixtureQueryHelper — V24D24.2 deriveRoundId (roundId determinism)")
class FixtureQueryHelperTest {

    private static final String CAREER_A = "career-abc-123";
    private static final String CAREER_B = "career-xyz-789";

    @Test
    @DisplayName("deriveRoundId is deterministic: same (careerId, round) → same UUID")
    void deriveRoundId_isDeterministic() {
        String first = FixtureQueryHelper.deriveRoundId(CAREER_A, 1);
        String second = FixtureQueryHelper.deriveRoundId(CAREER_A, 1);
        String third = FixtureQueryHelper.deriveRoundId(CAREER_A, 1);

        assertEquals(first, second, "Same inputs must produce identical roundId");
        assertEquals(second, third, "Same inputs must produce identical roundId (triple-check)");
    }

    @Test
    @DisplayName("deriveRoundId differs by round number (round 1 ≠ round 2)")
    void deriveRoundId_differsByRound() {
        String round1 = FixtureQueryHelper.deriveRoundId(CAREER_A, 1);
        String round2 = FixtureQueryHelper.deriveRoundId(CAREER_A, 2);

        assertNotEquals(round1, round2, "Different rounds must produce different roundIds");
        assertNotEquals(round1, FixtureQueryHelper.deriveRoundId(CAREER_A, 38),
                "Round 1 and round 38 (typical season end) must differ");
    }

    @Test
    @DisplayName("deriveRoundId differs by careerId (same round, different career → different roundId)")
    void deriveRoundId_differsByCareerId() {
        String round1CareerA = FixtureQueryHelper.deriveRoundId(CAREER_A, 1);
        String round1CareerB = FixtureQueryHelper.deriveRoundId(CAREER_B, 1);

        assertNotEquals(round1CareerA, round1CareerB,
                "Same round but different careers must produce different roundIds");
    }

    @Test
    @DisplayName("deriveRoundId returns a valid UUID string")
    void deriveRoundId_isValidUuid() {
        String roundId = FixtureQueryHelper.deriveRoundId(CAREER_A, 1);

        assertNotNull(roundId, "roundId must not be null");
        assertDoesNotThrow(() -> java.util.UUID.fromString(roundId),
                "roundId must parse as a valid UUID — front uses UUID.fromString() before POST");
    }

    @Test
    @DisplayName("deriveRoundId with null careerId → null (graceful degradation)")
    void deriveRoundId_nullCareerId_returnsNull() {
        assertNull(FixtureQueryHelper.deriveRoundId(null, 1),
                "Null careerId must yield null — ad-hoc helper callers pass null careerId");
    }
}