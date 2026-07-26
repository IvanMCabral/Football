package com.footballmanager.domain.model.entity;

import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * <p>Validates the new 18-arg canonical constructor shape and the
 * 9-arg backward-compatibility constructor:
 * <ul>
 *       {@code homePlayerRatings}, {@code awayPlayerRatings} to empty lists
 *       and {@code substitutionsRemaining} to 5 (full quota).</li>
 *   <li>{@code canonical18Arg_preservesAllFields} — the new 18-arg shape
 *       carries the player ratings and substitutions remaining fields.</li>
 * </ul>
 */
public class MatchStateSnapshotTest {

    @Test
    void legacyBackCompat9Arg_defaultsV25D79FieldsToSafeValues() {
        UUID matchId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        Score score = new Score(1, 0);
        List<MatchEvent> events = new ArrayList<>();

        MatchStateSnapshot snap = new MatchStateSnapshot(
            matchId, homeId, awayId,
            35, MatchStatus.RUNNING, score, events,
            "career-1", "user-1"
        );

        // Pre-existing 9-arg-shape fields preserved.
        assertEquals(matchId, snap.matchId());
        assertEquals(homeId, snap.homeTeamId());
        assertEquals(awayId, snap.awayTeamId());
        assertEquals(35, snap.currentMinute());
        assertEquals(MatchStatus.RUNNING, snap.status());
        assertEquals(score, snap.score());
        assertSame(events, snap.events());
        assertEquals("career-1", snap.careerId());
        assertEquals("user-1", snap.userId());
        // Pre-existing 9-arg back-compat defaults for BE1 fields.
        assertEquals(50, snap.homePossession());
        assertEquals(50, snap.awayPossession());
        assertEquals("BALANCED", snap.homeStyle());
        assertEquals("BALANCED", snap.awayStyle());
        assertEquals("4-4-2", snap.homeFormation());
        assertEquals("4-4-2", snap.awayFormation());

        assertNotNull(snap.homePlayerRatings());
        assertNotNull(snap.awayPlayerRatings());
        assertTrue(snap.homePlayerRatings().isEmpty(),
            "homePlayerRatings should default to empty list");
        assertTrue(snap.awayPlayerRatings().isEmpty(),
            "awayPlayerRatings should default to empty list");
        assertEquals(5, snap.substitutionsRemaining(),
            "substitutionsRemaining should default to 5 (full quota)");
    }

    @Test
    void canonical18Arg_preservesAllFields() {
        UUID matchId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        Score score = new Score(2, 1);
        List<MatchEvent> events = new ArrayList<>();
        List<V24PlayerMatchRatingDto> homeRatings = List.of(
            new V24PlayerMatchRatingDto(
                "p-home-1", "Home Player 1", homeId.toString(), "GK",
                7.5, 0, 0, 0, 0, 0, 0, 0, 0, false, false)
        );
        List<V24PlayerMatchRatingDto> awayRatings = List.of(
            new V24PlayerMatchRatingDto(
                "p-away-1", "Away Player 1", awayId.toString(), "ATT",
                8.0, 1, 0, 2, 3, 0, 0, 0, 0, false, false)
        );

        MatchStateSnapshot snap = new MatchStateSnapshot(
            matchId, homeId, awayId,
            60, MatchStatus.RUNNING, score, events,
            "career-7", "user-7",
            55, 45, "ATTACKING", "DEFENSIVE", "4-3-3", "4-5-1",
            homeRatings, awayRatings,
            3 // 2 subs already used → 3 remaining
        );

        // 9-arg + BE1 fields preserved.
        assertEquals(matchId, snap.matchId());
        assertEquals(homeId, snap.homeTeamId());
        assertEquals(awayId, snap.awayTeamId());
        assertEquals(60, snap.currentMinute());
        assertEquals(MatchStatus.RUNNING, snap.status());
        assertEquals(score, snap.score());
        assertEquals(55, snap.homePossession());
        assertEquals(45, snap.awayPossession());
        assertEquals("ATTACKING", snap.homeStyle());
        assertEquals("DEFENSIVE", snap.awayStyle());
        assertEquals("4-3-3", snap.homeFormation());
        assertEquals("4-5-1", snap.awayFormation());

        assertSame(homeRatings, snap.homePlayerRatings(),
            "homePlayerRatings must be the exact list passed in");
        assertSame(awayRatings, snap.awayPlayerRatings(),
            "awayPlayerRatings must be the exact list passed in");
        assertEquals(1, snap.homePlayerRatings().size());
        assertEquals(1, snap.awayPlayerRatings().size());
        assertEquals(3, snap.substitutionsRemaining());
    }
}
