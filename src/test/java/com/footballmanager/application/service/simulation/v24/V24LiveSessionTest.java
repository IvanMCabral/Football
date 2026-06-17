package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE-MATCH-F1-POC: unit tests for {@link V24LiveSession}.
 *
 * <p>Critical test in this file is
 * {@code recordManualSubstitution_doesNotAlterResult()} — it enforces D1=B
 * (manual substitutions are UI-only and do NOT alter the match result).
 *
 * <p>The match engine pre-simulates the full 90-minute timeline on the first
 * {@code tick()}; subsequent ticks only filter cached events. Injecting a
 * manual substitution via {@code recordManualSubstitution} adds the event to
 * the visible timeline but does NOT recompute homeGoals/awayGoals.
 */
class V24LiveSessionTest {

    private V24LiveSession session;
    private V24MatchContext context;
    private String homeTeamId;
    private String awayTeamId;

    @BeforeEach
    void setUp() {
        homeTeamId = "team-home";
        awayTeamId = "team-away";
        context = buildContext();
        session = new V24LiveSession(context, 42L);
        // Tick once so the engine runs the pre-simulation and sets homeGoals/awayGoals.
        // Without this, currentMinute is still 0 and homeGoals/awayGoals are 0.
        session.tick();
    }

    @Test
    @DisplayName("recordManualSubstitution adds SUBSTITUTION event to accumulatedEvents")
    void recordManualSubstitution_addsEventToAccumulatedEvents() {
        // Snapshot baseline (post-tick #1)
        int baselineSize = session.accumulatedEvents().size();
        int baselineMinute = session.currentMinute();

        // Build a valid SUBSTITUTION event
        V24MatchEvent event = new V24MatchEvent(
            baselineMinute,
            V24MatchEventType.SUBSTITUTION,
            homeTeamId,
            "home-starter-0",
            "Home Starter 0",
            "home-bench-0",
            "Home Bench 0",
            0.0,
            "Substitution: Home Bench 0 on for Home Starter 0"
        );

        session.recordManualSubstitution(event);

        // accumulatedEvents grew by exactly 1
        assertEquals(baselineSize + 1, session.accumulatedEvents().size());
        // The latest event is our manual substitution
        V24MatchEvent last = session.accumulatedEvents().get(
            session.accumulatedEvents().size() - 1);
        assertEquals(V24MatchEventType.SUBSTITUTION, last.type());
        assertEquals("home-starter-0", last.playerId());
        assertEquals("home-bench-0", last.relatedPlayerId());
        assertEquals(baselineMinute, last.minute());
    }

    @Test
    @DisplayName("CRITICAL D1=B: recordManualSubstitution does NOT alter homeGoals/awayGoals")
    void recordManualSubstitution_doesNotAlterResult() {
        // Snapshot baseline (post-tick #1, homeGoals/awayGoals have been computed
        // by the engine's pre-simulation).
        // Tick a few more times so goals may have been scored.
        for (int i = 0; i < 10; i++) {
            session.tick();
        }
        int preHomeGoals = countGoals(session.accumulatedEvents(), homeTeamId);
        int preAwayGoals = countGoals(session.accumulatedEvents(), awayTeamId);
        int preTotalEvents = session.accumulatedEvents().size();
        int currentMinute = session.currentMinute();

        // Inject a manual substitution event.
        V24MatchEvent subEvent = new V24MatchEvent(
            currentMinute,
            V24MatchEventType.SUBSTITUTION,
            homeTeamId,
            "home-starter-0",
            "Home Starter 0",
            "home-bench-0",
            "Home Bench 0",
            0.0,
            "Substitution: Home Bench 0 on for Home Starter 0"
        );
        session.recordManualSubstitution(subEvent);

        // CRITICAL D1=B ASSERTIONS:
        // 1. Goals count from accumulatedEvents is UNCHANGED.
        assertEquals(preHomeGoals, countGoals(session.accumulatedEvents(), homeTeamId),
            "D1=B violated: homeGoals changed after manual substitution");
        assertEquals(preAwayGoals, countGoals(session.accumulatedEvents(), awayTeamId),
            "D1=B violated: awayGoals changed after manual substitution");
        // 2. The total events grew by exactly 1 (the substitution event).
        assertEquals(preTotalEvents + 1, session.accumulatedEvents().size(),
            "Substitution event must be appended exactly once");
        // 3. Next snapshot reflects the same goals (substitution is not a goal).
        V24LiveSnapshot nextSnapshot = session.tick();
        assertEquals(preHomeGoals, nextSnapshot.homeGoals(),
            "D1=B violated: next snapshot.homeGoals changed after manual substitution");
        assertEquals(preAwayGoals, nextSnapshot.awayGoals(),
            "D1=B violated: next snapshot.awayGoals changed after manual substitution");
    }

    @Test
    @DisplayName("recordManualSubstitution throws when event type is not SUBSTITUTION")
    void recordManualSubstitution_rejectsNonSubstitutionEvent() {
        V24MatchEvent goalEvent = new V24MatchEvent(
            1,
            V24MatchEventType.GOAL,
            homeTeamId,
            "home-starter-0",
            "Home Starter 0",
            null,
            null,
            0.5,
            "GOAL!"
        );
        assertThrows(IllegalArgumentException.class,
            () -> session.recordManualSubstitution(goalEvent));
    }

    @Test
    @DisplayName("recordManualSubstitution throws when match is finished")
    void recordManualSubstitution_throwsWhenMatchFinished() {
        // Tick past 90 minutes to mark as finished
        for (int i = 0; i < 95; i++) {
            session.tick();
        }
        assertTrue(session.isFinished());

        V24MatchEvent subEvent = new V24MatchEvent(
            90,
            V24MatchEventType.SUBSTITUTION,
            homeTeamId,
            "home-starter-0",
            "Home Starter 0",
            "home-bench-0",
            "Home Bench 0",
            0.0,
            "Substitution: Home Bench 0 on for Home Starter 0"
        );
        assertThrows(IllegalStateException.class,
            () -> session.recordManualSubstitution(subEvent));
    }

    @Test
    @DisplayName("accumulatedEvents returns an unmodifiable view")
    void accumulatedEvents_isUnmodifiable() {
        V24MatchEvent subEvent = new V24MatchEvent(
            1,
            V24MatchEventType.SUBSTITUTION,
            homeTeamId,
            "x",
            "X",
            "y",
            "Y",
            0.0,
            "x→y"
        );
        session.recordManualSubstitution(subEvent);

        List<V24MatchEvent> view = session.accumulatedEvents();
        assertThrows(UnsupportedOperationException.class,
            () -> view.add(subEvent),
            "accumulatedEvents() should return an unmodifiable view");
    }

    @Test
    @DisplayName("currentMinute and context accessors return the expected values")
    void accessors_returnExpectedValues() {
        // currentMinute reflects the latest tick (started at 0, ticked once -> 1).
        assertEquals(1, session.currentMinute());
        assertNotNull(session.context());
        assertEquals(context, session.context());
        // Accumulated events is non-null
        assertNotNull(session.accumulatedEvents());
        assertFalse(session.accumulatedEvents().isEmpty());
    }

    // ========== Helpers ==========

    private V24MatchContext buildContext() {
        SessionTeam homeTeam = SessionTeam.custom(homeTeamId, "Home FC", "ARG",
            BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom(awayTeamId, "Away FC", "BRA",
            BigDecimal.valueOf(1_000_000L), "4-4-2");

        List<SessionPlayer> homeStarting = makePlayers(homeTeamId, "starter", 11);
        List<SessionPlayer> homeBench = makePlayers(homeTeamId, "bench", 5);
        List<SessionPlayer> awayStarting = makePlayers(awayTeamId, "starter", 11);
        List<SessionPlayer> awayBench = makePlayers(awayTeamId, "bench", 5);

        return new V24MatchContext(
            "match-1",
            homeTeamId,
            awayTeamId,
            homeTeam,
            awayTeam,
            homeStarting,
            awayStarting,
            homeBench,
            awayBench,
            "4-3-3",
            "4-4-2",
            TeamStyle.BALANCED,
            TeamStyle.BALANCED
        );
    }

    private List<SessionPlayer> makePlayers(String teamId, String suffix, int count) {
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String position = (i == 0) ? "GK"
                : (i <= 4) ? "DEF"
                : (i <= 7) ? "MID"
                : (i <= 9) ? "WINGER" : "ATT";
            String id = teamId + "-" + suffix + "-" + i;
            SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                70, 70, 70, 70, 70, 70, BigDecimal.valueOf(70000L));
            sp.setEnergy(100);
            players.add(sp);
        }
        return players;
    }

    private int countGoals(List<V24MatchEvent> events, String teamId) {
        int count = 0;
        for (V24MatchEvent e : events) {
            if (e.type() == V24MatchEventType.GOAL
                && e.teamId() != null
                && e.teamId().equals(teamId)) {
                count++;
            }
        }
        return count;
    }

    // ========== LIVE-MATCH-F2-LIVE — Fase 0 RED tests ==========
    //
    // These 4 tests document the contract that the engine refactor (Fase 1,
    // Opcion A replay incremental) MUST satisfy. They are RED today because
    // the engine pre-simulates the full 90-minute match on tick #0 and caches
    // the result — manual substitutions only append to the visual event
    // cache without recomputing goals. Once the refactor lands, all 4 tests
    // must turn GREEN.
    //
    // See: C:\Users\ichu_\.mavis\scratchpads\mvs_3f18031aaa7b4cd6a4e35a40d2a83f30\
    //      engine-refactor-analysis.md (sections 1.1, 1.2, 6)

    /**
     * F2 RED #1: a manager-applied substitution MUST alter the match result
     * relative to a no-substitution baseline (same seed, same context).
     *
     * <p>Today this test FAILS because the engine pre-simulates on tick #0 with
     * the original context and the substitution is only appended to the visual
     * event cache — homeGoals/awayGoals remain identical to the baseline.
     *
     * <p>Post-refactor: the substitution must invalidate the simulation cache
     * (or trigger a replay) so that the bench player's attributes feed into
     * subsequent goal/xG/shots computation. The final result MUST differ.
     */
    @Test
    @DisplayName("F2 RED: manual substitution alters match result (different from no-sub baseline)")
    void recordManualSubstitution_altersResult_differentFromBaseline() {
        // Baseline: same seed/context, NO substitutions, run to completion.
        V24LiveSession baselineSession = new V24LiveSession(context, 42L);
        for (int i = 0; i < 90; i++) baselineSession.tick();
        V24DetailedMatchResult baselineResult = baselineSession.finalResult();

        // Treatment: same seed/context, apply a substitution at minute 30 with
        // a higher-attack bench player (home-starter-9 is ATT, home-bench-9 is ATT).
        // The substitution should give the new player 60 minutes of game time
        // to influence the result — measurably different outcome expected.
        V24LiveSession subSession = new V24LiveSession(context, 42L);
        V24MatchEvent subEvent = buildManualSubstitutionEvent(
            homeTeamId, "home-starter-9", "Home Starter 9",
            "home-bench-9", "Home Bench 9", 30);
        subSession.recordManualSubstitution(subEvent);
        for (int i = 0; i < 90; i++) subSession.tick();
        V24DetailedMatchResult subResult = subSession.finalResult();

        // ASSERT: at least one of homeGoals/awayGoals must differ. Comparing
        // both is the strongest signal — even one goal difference proves the
        // substitution influenced goal scoring.
        boolean homeGoalsDiffer = baselineResult.homeGoals() != subResult.homeGoals();
        boolean awayGoalsDiffer = baselineResult.awayGoals() != subResult.awayGoals();
        assertTrue(homeGoalsDiffer || awayGoalsDiffer,
            "F2 RED violated: manual substitution did not alter the match result. "
            + "baseline(homeGoals=" + baselineResult.homeGoals()
            + ", awayGoals=" + baselineResult.awayGoals() + ") "
            + "== substitution(homeGoals=" + subResult.homeGoals()
            + ", awayGoals=" + subResult.awayGoals() + "). "
            + "This means the engine still pre-simulates without considering manager actions.");
    }

    /**
     * F2 RED #2: determinism contract — same seed + same context + same
     * substitution(s) MUST produce the same final result.
     *
     * <p>Today this test may PASS trivially because the substitution does not
     * influence the result at all, so two runs with the same seed produce
     * identical results regardless of substitutions. Post-refactor the test
     * still passes but now it actually exercises the determinism invariant
     * through the replay path.
     *
     * <p>If this test fails post-refactor, the replay mechanism is not
     * consuming doubles in a deterministic order — a regression of the V24
     * determinism guarantee.
     */
    @Test
    @DisplayName("F2 RED: determinism — same seed + same substitutions = same result")
    void determinism_sameSeedSameSubstitutions_sameResult() {
        // First run
        V24LiveSession runA = new V24LiveSession(context, 42L);
        runA.recordManualSubstitution(buildManualSubstitutionEvent(
            homeTeamId, "home-starter-9", "Home Starter 9",
            "home-bench-9", "Home Bench 9", 30));
        for (int i = 0; i < 90; i++) runA.tick();
        V24DetailedMatchResult resultA = runA.finalResult();

        // Second run from scratch, identical setup
        V24LiveSession runB = new V24LiveSession(context, 42L);
        runB.recordManualSubstitution(buildManualSubstitutionEvent(
            homeTeamId, "home-starter-9", "Home Starter 9",
            "home-bench-9", "Home Bench 9", 30));
        for (int i = 0; i < 90; i++) runB.tick();
        V24DetailedMatchResult resultB = runB.finalResult();

        // ASSERT: same seed + same context + same substitution = identical result.
        assertEquals(resultA.homeGoals(), resultB.homeGoals(),
            "F2 determinism violated: homeGoals differs across identical runs "
            + "(runA=" + resultA.homeGoals() + ", runB=" + resultB.homeGoals() + ")");
        assertEquals(resultA.awayGoals(), resultB.awayGoals(),
            "F2 determinism violated: awayGoals differs across identical runs "
            + "(runA=" + resultA.awayGoals() + ", runB=" + resultB.awayGoals() + ")");
    }

    /**
     * F2 RED #3: the moment a substitution is applied MUST influence the
     * outcome. Substituting a fresh, high-attack player at minute 1 gives
     * them ~89 minutes of influence; substituting them at minute 89 gives
     * them only 1 minute. The two outcomes MUST differ.
     *
     * <p>Today this test FAILS because both substitutions are visual-only —
     * the engine never replays, so the final result is identical regardless
     * of when the substitution is recorded.
     *
     * <p>Note: V24MatchEvent validates {@code minute in [1, 130]}, so we use
     * minute 1 (not 0) for the "early" case. The 88-minute delta vs the late
     * case still produces a measurable outcome difference once the refactor
     * is in place.
     */
    @Test
    @DisplayName("F2 RED: early (min 1) vs late (min 89) substitution produces different outcomes")
    void earlyVsLateSubstitution_producesDifferentOutcomes() {
        // Early substitution: same player (home-bench-9, ATT) comes on at minute 1
        // (~89 minutes of play for the bench player).
        V24LiveSession earlySession = new V24LiveSession(context, 42L);
        earlySession.recordManualSubstitution(buildManualSubstitutionEvent(
            homeTeamId, "home-starter-9", "Home Starter 9",
            "home-bench-9", "Home Bench 9", 1));
        for (int i = 0; i < 90; i++) earlySession.tick();
        V24DetailedMatchResult earlyResult = earlySession.finalResult();

        // Late substitution: SAME bench player comes on at minute 89 (1 min of play).
        V24LiveSession lateSession = new V24LiveSession(context, 42L);
        lateSession.recordManualSubstitution(buildManualSubstitutionEvent(
            homeTeamId, "home-starter-9", "Home Starter 9",
            "home-bench-9", "Home Bench 9", 89));
        for (int i = 0; i < 90; i++) lateSession.tick();
        V24DetailedMatchResult lateResult = lateSession.finalResult();

        // ASSERT: at least one of homeGoals/awayGoals must differ. The early
        // sub gives ~89 minutes of influence, the late gives 1 minute — the
        // cumulative goal output should be measurably different.
        boolean homeGoalsDiffer = earlyResult.homeGoals() != lateResult.homeGoals();
        boolean awayGoalsDiffer = earlyResult.awayGoals() != lateResult.awayGoals();
        assertTrue(homeGoalsDiffer || awayGoalsDiffer,
            "F2 RED violated: substituting at minute 1 vs minute 89 produced identical "
            + "results. early(homeGoals=" + earlyResult.homeGoals()
            + ", awayGoals=" + earlyResult.awayGoals() + ") "
            + "== late(homeGoals=" + lateResult.homeGoals()
            + ", awayGoals=" + lateResult.awayGoals() + "). "
            + "This means the substitution timing does not influence the outcome.");
    }

    /**
     * F2 RED #4: an invalid substitution (player not in starting XI) MUST be
     * rejected with an IllegalArgumentException (or IllegalStateException) AND
     * the match result MUST remain unchanged.
     *
     * <p>Today this test FAILS because {@code recordManualSubstitution} does
     * NOT validate that the playerOffId is actually in the starting XI — it
     * only checks {@code event.type() == SUBSTITUTION} and {@code !finished}.
     * Any V24MatchEvent with type=SUBSTITUTION is accepted into accumulatedEvents,
     * even if the playerId references a non-existent player.
     *
     * <p>Post-refactor: the engine must validate the substitution (playerOff
     * exists, playerOn is on the bench, position compatibility, max 5 subs)
     * before committing it to the timeline.
     */
    @Test
    @DisplayName("F2 RED: invalid substitution (non-existent playerOffId) is rejected + result unchanged")
    void invalidSubstitution_stillFails() {
        // Capture baseline state by running the full match.
        V24LiveSession baselineSession = new V24LiveSession(context, 42L);
        for (int i = 0; i < 90; i++) baselineSession.tick();
        V24DetailedMatchResult baselineResult = baselineSession.finalResult();

        // Attempt an invalid substitution: playerOffId does not exist in the XI.
        V24LiveSession invalidSession = new V24LiveSession(context, 42L);
        V24MatchEvent invalidSub = buildManualSubstitutionEvent(
            homeTeamId,
            "nonexistent-player-xyz", "Nonexistent Player",
            "home-bench-0", "Home Bench 0",
            30);

        // ASSERT (a): the invalid substitution must throw an exception.
        assertThrows(RuntimeException.class,
            () -> invalidSession.recordManualSubstitution(invalidSub),
            "F2 RED violated: recordManualSubstitution accepted an invalid substitution "
            + "(playerOffId='nonexistent-player-xyz' is not in the starting XI). "
            + "The engine must validate substitutions before accepting them.");

        // Run to completion — the match result must be unchanged from baseline.
        for (int i = 0; i < 90; i++) invalidSession.tick();
        V24DetailedMatchResult invalidResult = invalidSession.finalResult();

        // ASSERT (b): result must equal baseline (the rejected sub changed nothing).
        assertEquals(baselineResult.homeGoals(), invalidResult.homeGoals(),
            "F2 invariant violated: rejected invalid substitution altered homeGoals");
        assertEquals(baselineResult.awayGoals(), invalidResult.awayGoals(),
            "F2 invariant violated: rejected invalid substitution altered awayGoals");
    }

    // ========== Helpers — F2 RED tests ==========

    /**
     * Build a SUBSTITUTION event with the given players and minute. Pure
     * factory — does not touch any session state.
     */
    private V24MatchEvent buildManualSubstitutionEvent(String teamId,
                                                       String playerOffId,
                                                       String playerOffName,
                                                       String playerOnId,
                                                       String playerOnName,
                                                       int minute) {
        return new V24MatchEvent(
            minute,
            V24MatchEventType.SUBSTITUTION,
            teamId,
            playerOffId,
            playerOffName,
            playerOnId,
            playerOnName,
            0.0,
            "Substitution: " + playerOnName + " on for " + playerOffName
        );
    }
}
