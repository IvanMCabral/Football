package com.footballmanager.application.service.match.session;

import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24LiveSnapshot;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.Score;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D79: integration test for {@link MatchSession#adaptV24Snapshot(V24LiveSnapshot)}.
 *
 * <p>Drives the SSE-payload adapter with a controlled {@link V24LiveSnapshot}
 * to validate that:
 * <ul>
 *   <li>{@code homePlayerRatings} / {@code awayPlayerRatings} are computed
 *       from the live (partial) timeline via {@link
 *       com.footballmanager.application.service.simulation.v24.V24PlayerMatchStatsModel}.
 *       Each player in the context gets exactly one rating entry; ratings
 *       reflect the partial timeline so live stats evolve minute-by-minute.</li>
 *   <li>{@code substitutionsRemaining} starts at 5 (full quota) when no
 *       SUBSTITUTION events are present in the snapshot, decrements by 1
 *       per SUBSTITUTION event, and floors at 0.</li>
 * </ul>
 *
 * <p>{@code adaptV24Snapshot} is package-private (not part of the public API)
 * — this test lives in the same package so it can drive the conversion
 * directly without spinning up the entire Spring context.
 */
public class MatchSessionV25D79Test {

    private static final UUID MATCH_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");

    @Test
    void adaptV24Snapshot_carriesV25D79Fields_onControlledSnapshot() {
        // (1) Build a minimal V24MatchContext: 7 starters per side (LineupRules.MIN), 0 bench.
        UUID homeTeamUuid = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID awayTeamUuid = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        SessionTeam homeTeam = SessionTeam.custom(
                homeTeamUuid.toString(), "Home FC", "ES",
                new BigDecimal("1000000"), "4-4-2");
        SessionTeam awayTeam = SessionTeam.custom(
                awayTeamUuid.toString(), "Away FC", "ES",
                new BigDecimal("1000000"), "4-4-2");

        List<SessionPlayer> homeStarting = roster("home", 7, 80);
        List<SessionPlayer> awayStarting = roster("away", 7, 80);

        V24MatchContext ctx = new V24MatchContext(
                MATCH_ID.toString(),
                homeTeamUuid.toString(),
                awayTeamUuid.toString(),
                homeTeam, awayTeam,
                homeStarting, awayStarting,
                List.of(), List.of(),
                "4-4-2", "4-4-2",
                TeamStyle.BALANCED, TeamStyle.BALANCED);

        // (2) Wire a real V24LiveSession — the engine ticks are deterministic
        // and we don't actually need to call tick() before adaptV24Snapshot()
        // because we drive the snapshot by hand below.
        long seed = 42L;
        V24LiveSession session = new V24LiveSession(ctx, seed);

        // (3) Build a MatchSession with the v24LiveSession. MatchState +
        // MatchTickHandler are required by the constructor but the test never
        // triggers the legacy path so they are just minimal stubs.
        MatchState legacyState = new MatchState(MATCH_ID);
        legacyState.setHomeTeamId(homeTeamUuid);
        legacyState.setAwayTeamId(awayTeamUuid);
        legacyState.setCareerId("career-v25d79");
        legacyState.setUserId("user-v25d79");
        MatchTickHandler tickHandler = new MatchTickHandler();
        MatchSession matchSession = new MatchSession(
                UUID.fromString("00000000-0000-0000-0000-00000000b001"),
                MATCH_ID, legacyState, tickHandler, session);

        // (4) Drive a controlled V24LiveSnapshot at minute 30 with one GOAL
        // event for the home side (so we see a live rating carry) and
        // 2 SUBSTITUTION events for the away team (so we see the decremented
        // counter).
        List<V24MatchEvent> liveEvents = new ArrayList<>();
        liveEvents.add(makeEvent(V24MatchEventType.GOAL, 25,
                "home-p1", "Home Striker", homeTeamUuid.toString(), null, 0.5));
        liveEvents.add(makeEvent(V24MatchEventType.SUBSTITUTION, 30,
                "away-off-1", "Away Off 1", awayTeamUuid.toString(),
                "away-on-1", 0.0));
        liveEvents.add(makeEvent(V24MatchEventType.SUBSTITUTION, 32,
                "away-off-2", "Away Off 2", awayTeamUuid.toString(),
                "away-on-2", 0.0));

        V24LiveSnapshot snap = new V24LiveSnapshot(
                MATCH_ID.toString(),
                /* minute */ 30,
                /* homeGoals */ 1,
                /* awayGoals */ 0,
                homeTeamUuid.toString(), awayTeamUuid.toString(),
                /* finished */ false,
                liveEvents,
                /* homePossession */ 55, /* awayPossession */ 45,
                "ATTACKING", "DEFENSIVE",
                "4-4-2", "4-4-2"
        );

        // (5) Adapt the V24LiveSnapshot to the SSE-facing MatchStateSnapshot.
        MatchStateSnapshot out = matchSession.adaptV24Snapshot(snap);

        // (6) Sanity: BE1 fields preserved.
        assertEquals(55, out.homePossession());
        assertEquals(45, out.awayPossession());
        assertEquals("ATTACKING", out.homeStyle());
        assertEquals("4-4-2", out.homeFormation());
        assertEquals(MATCH_ID, out.matchId());
        assertEquals("career-v25d79", out.careerId());
        assertEquals("user-v25d79", out.userId());

        // (7) V25D79 playerRatings: one rating entry per player in the team
        // (starting only here). 7 home + 7 away.
        assertNotNull(out.homePlayerRatings(),
                "homePlayerRatings must be a real list, not null");
        assertNotNull(out.awayPlayerRatings(),
                "awayPlayerRatings must be a real list, not null");
        assertEquals(7, out.homePlayerRatings().size(),
                "7 home starters should produce 7 rating entries");
        assertEquals(7, out.awayPlayerRatings().size(),
                "7 away starters should produce 7 rating entries");

        V24PlayerMatchRatingDto homeStriker = findByPlayerId(out.homePlayerRatings(), "home-p1");
        assertNotNull(homeStriker, "home strikers rating must be present");
        assertEquals(1, homeStriker.goals(),
                "home striker scored once in the live timeline");
        assertTrue(homeStriker.rating() > 6.0,
                "home striker rating should reflect the GOAL bonus (base 6.0 + GOAL_BONUS 0.8)");

        // (8) V25D79 substitutionsRemaining: 5 - 2 SUBSTITUTION events = 3.
        assertEquals(3, out.substitutionsRemaining(),
                "5 sub quota minus 2 SUBSTITUTION events = 3 remaining");

        MatchEvent firstSub = out.events().stream()
                .filter(event -> event.getEventType() == MatchEvent.EventType.SUBSTITUTION)
                .findFirst()
                .orElseThrow();
        assertEquals("away-off-1", firstSub.getPlayerId(),
                "SUBSTITUTION playerId must be the player leaving");
        assertEquals("Away Off 1", firstSub.getPlayerName(),
                "SUBSTITUTION playerName must be the player leaving");
        assertEquals("away-on-1", firstSub.getRelatedPlayerId(),
                "SUBSTITUTION relatedPlayerId must be the player entering so reloads can rebuild the XI");
        assertEquals("Away On 1", firstSub.getRelatedPlayerName(),
                "SUBSTITUTION relatedPlayerName must be preserved");
        assertEquals("Away On 1", firstSub.getPlayerOnName(),
                "SUBSTITUTION playerOnName must remain populated for the live timeline");
        assertEquals(awayTeamUuid.toString(), firstSub.getTeamId(),
                "SUBSTITUTION teamId must be preserved for per-team quotas and modal reconstruction");
    }

    @Test
    void adaptV24Snapshot_substitutionsRemaining_floorsAtZero_whenManySubstitutionEvents() {
        // No V24MatchContext needed for this branch — we just exercise the
        // SUBSTITUTION counting on a v24LiveSession with an empty/no-context.
        // When ctx is null, ratings default to empty; substitutions still
        // come from the events list and that path is the regression sentinel.
        UUID homeTeamUuid = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        UUID awayTeamUuid = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        // Empty contexts are accepted because buildPlayerStates is null-safe
        // and the new homeStartingPlayers[] is just an empty list (which
        // would only matter for a real engine run; we never call tick()).
        SessionTeam homeTeam = SessionTeam.custom(
                homeTeamUuid.toString(), "Home FC", "ES",
                new BigDecimal("1000000"), "4-4-2");
        SessionTeam awayTeam = SessionTeam.custom(
                awayTeamUuid.toString(), "Away FC", "ES",
                new BigDecimal("1000000"), "4-4-2");
        List<SessionPlayer> homeStarting = roster("home", 7, 80);
        List<SessionPlayer> awayStarting = roster("away", 7, 80);

        V24MatchContext ctx = new V24MatchContext(
                MATCH_ID.toString(),
                homeTeamUuid.toString(),
                awayTeamUuid.toString(),
                homeTeam, awayTeam,
                homeStarting, awayStarting,
                List.of(), List.of(),
                "4-4-2", "4-4-2",
                TeamStyle.BALANCED, TeamStyle.BALANCED);
        V24LiveSession session = new V24LiveSession(ctx, 7L);

        MatchState legacyState = new MatchState(MATCH_ID);
        legacyState.setHomeTeamId(homeTeamUuid);
        legacyState.setAwayTeamId(awayTeamUuid);
        MatchSession matchSession = new MatchSession(
                UUID.fromString("00000000-0000-0000-0000-00000000b002"),
                MATCH_ID, legacyState, new MatchTickHandler(), session);

        // 8 SUBSTITUTION events > MAX_SUBSTITUTIONS (5) — the floor at 0
        // protects the UI from a negative counter if a buggy engine emits
        // more SUBSTITUTION events than the per-match cap.
        List<V24MatchEvent> overSubs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            overSubs.add(makeEvent(V24MatchEventType.SUBSTITUTION, 30 + i,
                    "away-off-" + i, "Away Off " + i,
                    awayTeamUuid.toString(), "away-on-" + i, 0.0));
        }
        V24LiveSnapshot snap = new V24LiveSnapshot(
                MATCH_ID.toString(), 60, 0, 0,
                homeTeamUuid.toString(), awayTeamUuid.toString(),
                false, overSubs, 50, 50,
                "BALANCED", "BALANCED", "4-4-2", "4-4-2"
        );

        MatchStateSnapshot out = matchSession.adaptV24Snapshot(snap);
        assertEquals(0, out.substitutionsRemaining(),
                "substitutionsRemaining must floor at 0 even when more SUBSTITUTION "
                    + "events than MAX_SUBSTITUTIONS (5) are present in the snapshot");
    }

    // ========== helpers ==========

    /** Build N SessionPlayers with predictable ids and attributes (overall = given ovr). */
    private static List<SessionPlayer> roster(String prefix, int n, int overall) {
        List<SessionPlayer> players = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            players.add(SessionPlayer.fromWorldPlayer(
                    prefix + "-p" + (i + 1),
                    prefix.toUpperCase() + " Player " + (i + 1),
                    "MID",
                    /* age */ 25,
                    /* overall */ overall));
        }
        return players;
    }

    private static V24MatchEvent makeEvent(
            V24MatchEventType type, int minute,
            String playerId, String playerName,
            String teamId, String relatedPlayerId,
            double xg) {
        return new V24MatchEvent(
                /* minute */ minute,
                /* type */ type,
                /* teamId */ teamId,
                /* playerId */ playerId,
                /* playerName */ playerName,
                /* relatedPlayerId */ relatedPlayerId,
                /* relatedPlayerName */ relatedPlayerId != null
                        ? relatedPlayerId.replace("-on-", " On ").replace("away", "Away")
                        : null,
                /* xg */ xg,
                /* description */ "test-event"
        );
    }

    /** Find a rating entry by playerId. Returns null when not present. */
    private static V24PlayerMatchRatingDto findByPlayerId(
            List<V24PlayerMatchRatingDto> ratings, String playerId) {
        for (V24PlayerMatchRatingDto r : ratings) {
            if (playerId.equals(r.playerId())) {
                return r;
            }
        }
        return null;
    }
}
