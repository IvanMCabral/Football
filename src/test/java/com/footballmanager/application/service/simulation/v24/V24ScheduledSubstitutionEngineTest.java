package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE-MATCH-F2-LIVE F2.5 (T3.3): engine-level test for deferred manual
 * substitutions. Verifies that the
 * {@link V24DetailedMatchEngine#simulate(V24MatchContext, long)} method
 * applies scheduled subs at the right minute inside the
 * {@code simulateWithRandom} loop.
 *
 * <p>Asserts:
 * <ol>
 *   <li>The timeline has exactly 1 SUBSTITUTION event at {@code minute == 30}.</li>
 *   <li>Events in minutes [0, 29] do NOT reference the new player
 *       (lineup is the original during the early match).</li>
 *   <li>Events in minutes [30, 89] reference the new player (lineup
 *       changed at minute 30 onward).</li>
 *   <li>{@code homeGoals/awayGoals} with the sub at minute 30 differ from
 *       a no-sub baseline (same seed).</li>
 *   <li>{@code homeGoals/awayGoals} with the sub at minute 30 differ
 *       from the same sub at minute 60 (same seed) — confirms the
 *       effectiveMinute is actually respected by the engine.</li>
 * </ol>
 */
class V24ScheduledSubstitutionEngineTest {

    private static final long SEED = 42L;

    @Test
    @DisplayName("F2.5: scheduledSub at minute 30 — lineup changes at minute 30 (5 asserts)")
    void scheduledSub_atMinute30_lineupChangesAtMinute30() {
        // Baseline: no sub, same seed, same context.
        V24MatchContext baselineCtx = buildContext();
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult baselineResult = engine.simulate(baselineCtx, SEED);
        assertNotNull(baselineResult);

        // Treatment: same context + a single scheduled sub at minute 30.
        // Uses ATT→WINGER (home-starter-10 → home-bench-4) so the swap
        // is position-compatible AND the bench WINGER has higher attack,
        // measurably affecting the engine's chance creation.
        V24MatchContext treatmentCtx = baselineCtx.withManualSubstitution(
            "home", "home-starter-10", "home-bench-4", 30);
        V24DetailedMatchResult treatmentResult = engine.simulate(treatmentCtx, SEED);

        // ----- Assert 1: timeline has exactly 1 SUBSTITUTION event at minute 30 -----
        List<V24MatchEvent> events = treatmentResult.timeline().events();
        int subCountAt30 = 0;
        for (V24MatchEvent e : events) {
            if (e.type() == V24MatchEventType.SUBSTITUTION && e.minute() == 30) {
                subCountAt30++;
            }
        }
        assertEquals(1, subCountAt30,
            "F2.5: timeline must contain exactly 1 SUBSTITUTION event at minute 30; got " + subCountAt30);

        // ----- Assert 2: events in minutes [0, 29] do NOT reference home-bench-4 -----
        boolean earlyHasBench4 = false;
        for (V24MatchEvent e : events) {
            if (e.minute() < 30) {
                if (e.playerId() != null && e.playerId().equals("home-bench-4")) {
                    earlyHasBench4 = true;
                    break;
                }
                if (e.relatedPlayerId() != null && e.relatedPlayerId().equals("home-bench-4")) {
                    earlyHasBench4 = true;
                    break;
                }
            }
        }
        assertEquals(false, earlyHasBench4,
            "F2.5: events in minutes [0, 29] must NOT reference the new player home-bench-4 "
            + "(lineup is the original during the early match). Found one in: "
            + events.stream().filter(e -> e.minute() < 30).toList());

        // ----- Assert 3: events in minutes [30, 89] reference home-bench-4 -----
        boolean lateHasBench4 = false;
        for (V24MatchEvent e : events) {
            if (e.minute() >= 30) {
                if ((e.playerId() != null && e.playerId().equals("home-bench-4"))
                        || (e.relatedPlayerId() != null && e.relatedPlayerId().equals("home-bench-4"))) {
                    lateHasBench4 = true;
                    break;
                }
            }
        }
        assertEquals(true, lateHasBench4,
            "F2.5: events in minutes [30, 89] must reference the new player home-bench-4 "
            + "(lineup changed at minute 30 onward). No event references home-bench-4 "
            + "in the post-sub window: "
            + events.stream().filter(e -> e.minute() >= 30).toList());

        // ----- Assert 4: homeGoals/awayGoals with sub at minute 30 differ from baseline -----
        assertNotEquals(baselineResult.homeGoals(), treatmentResult.homeGoals(),
            "F2.5: sub at minute 30 should alter homeGoals vs no-sub baseline. "
            + "baseline=" + baselineResult.homeGoals() + ", treatment=" + treatmentResult.homeGoals());
        assertNotEquals(baselineResult.awayGoals(), treatmentResult.awayGoals(),
            "F2.5: sub at minute 30 should alter awayGoals vs no-sub baseline. "
            + "baseline=" + baselineResult.awayGoals() + ", treatment=" + treatmentResult.awayGoals());

        // ----- Assert 5: homeGoals with sub at minute 30 != homeGoals with sub at minute 60 -----
        V24MatchContext at30Ctx = baselineCtx.withManualSubstitution(
            "home", "home-starter-10", "home-bench-4", 30);
        V24MatchContext at60Ctx = baselineCtx.withManualSubstitution(
            "home", "home-starter-10", "home-bench-4", 60);
        V24DetailedMatchResult at30 = engine.simulate(at30Ctx, SEED);
        V24DetailedMatchResult at60 = engine.simulate(at60Ctx, SEED);
        assertNotEquals(at30.homeGoals(), at60.homeGoals(),
            "F2.5: homeGoals with sub at minute 30 should differ from minute 60 "
            + "(effectiveMinute must be respected by the engine). "
            + "minute30=" + at30.homeGoals() + ", minute60=" + at60.homeGoals());
        // awayGoals might be equal in some seed setups, but at least one of the two should differ.
        assertTrue(at30.homeGoals() != at60.homeGoals() || at30.awayGoals() != at60.awayGoals(),
            "F2.5: at least one of (homeGoals, awayGoals) must differ between "
            + "sub@30 and sub@60. 30=(h=" + at30.homeGoals() + ",a=" + at30.awayGoals()
            + "), 60=(h=" + at60.homeGoals() + ",a=" + at60.awayGoals() + ")");
    }

    @Test
    @DisplayName("F2.5: scheduledSub at minute 1 fires on the first iteration (clock starts at 1)")
    void scheduledSub_atMinute1_firesImmediately() {
        V24MatchContext baselineCtx = buildContext();
        V24MatchContext treatmentCtx = baselineCtx.withManualSubstitution(
            "home", "home-starter-10", "home-bench-4", 1);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult treatment = engine.simulate(treatmentCtx, SEED);

        List<V24MatchEvent> events = treatment.timeline().events();
        int subAt1 = 0;
        for (V24MatchEvent e : events) {
            if (e.type() == V24MatchEventType.SUBSTITUTION && e.minute() == 1) {
                subAt1++;
            }
        }
        assertEquals(1, subAt1,
            "F2.5: sub with effectiveMinute=1 must fire on the first iteration (minute 1). "
            + "Got " + subAt1 + " SUBSTITUTION events at minute 1.");
    }

    // ========== Fixture helpers ==========

    private V24MatchContext buildContext() {
        SessionTeam homeTeam = SessionTeam.custom("home", "Home FC", "ARG",
            BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom("away", "Away FC", "BRA",
            BigDecimal.valueOf(1_000_000L), "4-4-2");
        // Bench players have higher attack/defense/technique than starters
        // so the F2.5 deferred-sub wire measurably alters the engine's
        // goal output. bench-4 is a WINGER (not DEF) so the F2.5 tests
        // can use WINGER→WINGER swaps (position-compatible).
        return new V24MatchContext(
            "match-f2.5",
            "home", "away",
            homeTeam, awayTeam,
            makePlayers("home", "starter", 11, 70),
            makePlayers("away", "starter", 11, 70),
            makePlayers("home", "bench", 5, 80),
            makePlayers("away", "bench", 5, 80),
            "4-3-3", "4-4-2",
            TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private List<SessionPlayer> makePlayers(String teamId, String suffix, int count, int ovr) {
        List<SessionPlayer> players = new ArrayList<>();
        boolean isBench = "bench".equals(suffix);
        int attack = isBench ? 80 : 70;
        int defense = isBench ? 75 : 70;
        int technique = isBench ? 78 : 70;
        int speed = isBench ? 80 : 70;
        for (int i = 0; i < count; i++) {
            // F2.5: bench-4 is a WINGER (not DEF) so the F2.5 tests can
            // use WINGER→WINGER swaps (position-compatible).
            String position;
            if (isBench) {
                position = (i == 0) ? "GK"
                    : (i <= 3) ? "DEF"
                    : "WINGER";
            } else {
                position = (i == 0) ? "GK"
                    : (i <= 4) ? "DEF"
                    : (i <= 7) ? "MID"
                    : (i <= 9) ? "WINGER" : "ATT";
            }
            String id = teamId + "-" + suffix + "-" + i;
            SessionPlayer sp = SessionPlayer.custom(id, 25, position,
                attack, defense, technique, speed, 70, 70,
                BigDecimal.valueOf(70_000L));
            sp.setSessionPlayerId(id);
            sp.setEnergy(100);
            players.add(sp);
        }
        return players;
    }
}





