package com.footballmanager.application.service.simulation;

import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10C2: MatchResultDataAdapter unit tests.
 * Validates that V23 MatchResult maps correctly to MatchResultData.
 * Events and summary are ignored — only 6 fields mapped.
 */
class MatchResultDataAdapterTest {

    // ========== Test 1: all 6 fields mapped correctly ==========

    @Test
    void fromMatchResult_mapsAllSixFields() {
        MatchResult result = MatchResult.of(
                2, 1,       // homeGoals=2, awayGoals=1
                58, 42,     // homePossession=58, awayPossession=42
                12, 8,      // homeShots=12, awayShots=8
                List.of(),  // events — ignored
                "summary"   // summary — ignored
        );

        MatchFixture.MatchResultData data = MatchResultDataAdapter.fromMatchResult(result);

        assertEquals(2, data.homeGoals);
        assertEquals(1, data.awayGoals);
        assertEquals(58, data.homePossession);
        assertEquals(42, data.awayPossession);
        assertEquals(12, data.homeShots);
        assertEquals(8, data.awayShots);
    }

    // ========== Test 2: events are ignored ==========

    @Test
    void fromMatchResult_ignoresEvents() {
        MatchResult result = MatchResult.of(
                3, 0,
                65, 35,
                15, 5,
                java.util.List.of(
                        com.footballmanager.domain.model.entity.MatchEvent.of(
                                com.footballmanager.domain.model.entity.MatchEvent.EventType.GOAL, 1, "player1", "goal"),
                        com.footballmanager.domain.model.entity.MatchEvent.of(
                                com.footballmanager.domain.model.entity.MatchEvent.EventType.GOAL, 45, "player2", "goal")
                ),
                "ignored"
        );

        MatchFixture.MatchResultData data = MatchResultDataAdapter.fromMatchResult(result);

        assertEquals(3, data.homeGoals);
        assertEquals(0, data.awayGoals);
        // events are not checked — they are discarded in the mapping
    }

    // ========== Test 3: summary is ignored ==========

    @Test
    void fromMatchResult_ignoresSummary() {
        MatchResult result = MatchResult.of(
                1, 1,
                50, 50,
                6, 6,
                List.of(),
                "Home team wins! Final score: 1-1"
        );

        MatchFixture.MatchResultData data = MatchResultDataAdapter.fromMatchResult(result);

        assertEquals(1, data.homeGoals);
        assertEquals(1, data.awayGoals);
        // summary is not checked — it is discarded in the mapping
    }

    // ========== Test 4: null result throws NullPointerException ==========

    @Test
    void fromMatchResult_nullResultThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            MatchResultDataAdapter.fromMatchResult(null);
        });
    }

    // ========== Test 5: zero values handled correctly ==========

    @Test
    void fromMatchResult_handlesZeroValues() {
        MatchResult result = MatchResult.of(
                0, 0,
                50, 50,
                3, 3,
                List.of(),
                "draw"
        );

        MatchFixture.MatchResultData data = MatchResultDataAdapter.fromMatchResult(result);

        assertEquals(0, data.homeGoals);
        assertEquals(0, data.awayGoals);
        assertEquals(50, data.homePossession);
        assertEquals(50, data.awayPossession);
        assertEquals(3, data.homeShots);
        assertEquals(3, data.awayShots);
    }

    // ========== Test 6: extreme values handled correctly ==========

    @Test
    void fromMatchResult_handlesExtremePossession() {
        MatchResult result = MatchResult.of(
                5, 0,
                90, 10,
                20, 3,
                List.of(),
                "blowout"
        );

        MatchFixture.MatchResultData data = MatchResultDataAdapter.fromMatchResult(result);

        assertEquals(90, data.homePossession);
        assertEquals(10, data.awayPossession);
    }
}