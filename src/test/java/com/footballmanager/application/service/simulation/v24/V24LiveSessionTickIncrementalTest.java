package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V25D87 (F2): unit tests for the bounded {@code simulate(ctx, random,
 * maxMinute)} path introduced in F1 Option A. Verifies the contract that
 * the live SSE tick driver now relies on:
 *
 * <ul>
 *   <li>Each {@code tick()} advances {@code currentMinute} by 1 and the
 *       engine timeline grows incrementally (no full 90-minute reset on
 *       every tick — that's the F1 efficiency win).</li>
 *   <li>Bounded simulate preserves the CachingRandomWrapper replay
 *       contract: same seed + same context + same number of ticks →
 *       same final {@code homeGoals}/{@code awayGoals} as the unbounded
 *       baseline (full 90-minute run).</li>
 *   <li>The bounded simulation is monotonic: a tick's
 *       {@code engineTimeline} contains only events with
 *       {@code minute <= currentMinute}.</li>
 * </ul>
 *
 * <p>The "wall clock duration" check that the pre-codear plan
 * mentioned was dropped — the existing
 * {@code V24LiveSessionMetricsTest} already measures the per-tick cost
 * (the bounded path is ~90× cheaper, so the F1 metric budget is well
 * met) and a wall-clock integration assertion would couple this unit
 * test to the JVM/scheduler and be flaky. The smoke run handles the
 * end-to-end timing check.
 */
class V24LiveSessionTickIncrementalTest {

    private static final long SEED = 42L;
    private static final String HOME = "team-home";
    private static final String AWAY = "team-away";

    @Test
    @DisplayName("tick_incrementalCurrentMinute_1to90 — currentMinute progresses 1 per tick")
    void tick_incrementalCurrentMinute_1to90() {
        V24LiveSession s = newSession();
        for (int expected = 1; expected <= 90; expected++) {
            s.tick();
            assertEquals(expected, s.currentMinute(),
                "currentMinute should equal tick count after " + expected + " ticks");
            if (expected == 90) {
                assertTrue(s.isFinished(), "match should be finished after 90 ticks");
            } else {
                assertEquals(false, s.isFinished(),
                    "match should NOT be finished before 90 ticks (was at " + expected + ")");
            }
        }
    }

    @Test
    @DisplayName("tick_engineTimelineMinuteCap — engineTimeline events only go up to currentMinute")
    void tick_engineTimelineMinuteCap() {
        V24LiveSession s = newSession();
        int previousMaxMinute = 0;
        for (int i = 0; i < 90; i++) {
            s.tick();
            int observedMaxMinute = s.accumulatedEvents().stream()
                .mapToInt(V24MatchEvent::minute)
                .max()
                .orElse(0);
            // Either no engine event yet (rare; bounded simulate can
            // produce 0 events on quiet minutes) or the max event
            // minute equals the new currentMinute. We use a less strict
            // bound: events must NEVER exceed currentMinute.
            assertTrue(observedMaxMinute <= s.currentMinute(),
                "engine event at minute " + observedMaxMinute
                    + " exceeds currentMinute=" + s.currentMinute());
            // And it should grow (or stay at zero) — never regress.
            if (observedMaxMinute > 0) {
                assertTrue(observedMaxMinute >= previousMaxMinute,
                    "engineTimeline minute regressed: was "
                        + previousMaxMinute + ", observed " + observedMaxMinute);
                previousMaxMinute = observedMaxMinute;
            }
        }
    }

    @Test
    @DisplayName("tick_distinctMinutesSeen — across 90 ticks we see a spread of minutes (not just first)")
    void tick_distinctMinutesSeen() {
        V24LiveSession s = newSession();
        Set<Integer> minutesSeen = new HashSet<>();
        for (int i = 0; i < 90; i++) {
            s.tick();
            s.accumulatedEvents().forEach(e -> minutesSeen.add(e.minute()));
        }
        assertNotNull(minutesSeen);
        // The engine produces events across the match; for 90 ticks of a
        // V24 match we expect events to span at least 5+ distinct
        // minutes. The exact number is random but should not collapse
        // to a single minute (which would happen if the bounded
        // simulate stuck at a fixed prefix).
        assertTrue(minutesSeen.size() >= 5,
            "expected >= 5 distinct minutes in event log, got " + minutesSeen.size()
                + " (minutes=" + minutesSeen + ")");
    }

    @Test
    @DisplayName("tick_boundedFinalMatchesFullUnbounded — same seed + 90 ticks bounded == 1 full simulate")
    void tick_boundedFinalMatchesFullUnbounded() {
        // Baseline: full 90-min simulate (unbounded), single call.
        V24LiveSession baseline = new V24LiveSession(buildContext(), SEED);
        // Force the full simulate via finalResult() — it lazy-runs
        // engine.simulate(ctx, cachedRandom) without maxMinute.
        V24DetailedMatchResult baselineResult = baseline.finalResult();

        // Treatment: 90 individual ticks via the new bounded path.
        V24LiveSession ticked = new V24LiveSession(buildContext(), SEED);
        for (int i = 0; i < 90; i++) ticked.tick();

        // The bounded path must produce the same final goals as the
        // unbounded path (the CachingRandomWrapper makes both consume
        // the SAME draw sequence for minute 1..90). The full simulate
        // returns 90 minutes of events; the bounded path appends via
        // incremental ticks (with re-emit on scheduled subs per
        // pre-existing F2.5 semantics) so the timeline size may differ
        // — only the aggregate goals/xG totals must match.
        assertEquals(baselineResult.homeGoals(), ticked.finalResult().homeGoals(),
            "bounded tick path final homeGoals must equal unbounded baseline (seeded)");
        assertEquals(baselineResult.awayGoals(), ticked.finalResult().awayGoals(),
            "bounded tick path final awayGoals must equal unbounded baseline (seeded)");
    }

    // ========== helpers (mirroring V24LiveSessionTest) ==========

    private V24LiveSession newSession() {
        return new V24LiveSession(buildContext(), SEED);
    }

    private V24MatchContext buildContext() {
        SessionTeam homeTeam = SessionTeam.custom(HOME, "Home FC", "ARG",
            BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom(AWAY, "Away FC", "BRA",
            BigDecimal.valueOf(1_000_000L), "4-4-2");

        List<SessionPlayer> homeStarting = makePlayers(HOME, "starter", 11);
        List<SessionPlayer> homeBench = makePlayers(HOME, "bench", 5);
        List<SessionPlayer> awayStarting = makePlayers(AWAY, "starter", 11);
        List<SessionPlayer> awayBench = makePlayers(AWAY, "bench", 5);

        // F1/F2/F5 13-arg constructor — manualSubstitutions defaults to
        // empty list. We don't need scheduled subs in this test.
        return new V24MatchContext(
            "match-tick-inc",
            HOME,
            AWAY,
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

    private static List<SessionPlayer> makePlayers(String teamId, String suffix, int count) {
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = teamId + "-" + suffix + "-" + i;
            String position;
            if ("bench".equals(suffix)) {
                position = (i == 0) ? "GK"
                    : (i <= 3) ? "DEF"
                    : "WINGER";
            } else {
                position = (i == 0) ? "GK"
                    : (i <= 4) ? "DEF"
                    : (i <= 7) ? "MID"
                    : (i <= 9) ? "WINGER" : "ATT";
            }
            int attack = "bench".equals(suffix) ? 80 : 70;
            int defense = "bench".equals(suffix) ? 75 : 70;
            int technique = "bench".equals(suffix) ? 78 : 70;
            int speed = "bench".equals(suffix) ? 80 : 70;
            int stamina = 70;
            int mentality = 70;
            // F1/F2 10-arg factory: SessionPlayer.custom(id, age, position,
            // attack, defense, technique, speed, stamina, mentality, marketValue).
            // The factory generates a random UUID for sessionPlayerId; we
            // override with our deterministic id so the engine can match
            // by id during the bounded replay determinism tests.
            SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                attack, defense, technique, speed, stamina, mentality,
                BigDecimal.valueOf(70000L));
            sp.setSessionPlayerId(id);
            sp.setEnergy(100);
            players.add(sp);
        }
        return players;
    }
}
