package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Same seed + same context = identical result.
 */
class DetailedMatchEngineDeterminismTest {

    @Test
    void sameSeedProducesIdenticalResult() {
        MatchContext ctx = buildContext("match-det-1", 75, 75);

        DetailedMatchEngine engine = new DetailedMatchEngine();

        DetailedMatchResult r1 = engine.simulate(ctx, 42L);
        DetailedMatchResult r2 = engine.simulate(ctx, 42L);

        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals(r1.homeGoals(), r2.homeGoals(), "homeGoals identical");
        assertEquals(r1.awayGoals(), r2.awayGoals(), "awayGoals identical");
        assertEquals(r1.homeShots(), r2.homeShots(), "homeShots identical");
        assertEquals(r1.awayShots(), r2.awayShots(), "awayShots identical");
        assertEquals(r1.homePossession(), r2.homePossession(), "homePossession identical");
        assertEquals(r1.awayPossession(), r2.awayPossession(), "awayPossession identical");
        assertEquals(r1.homeXg(), r2.homeXg(), "homeXg identical");
        assertEquals(r1.awayXg(), r2.awayXg(), "awayXg identical");
        assertEquals(r1.summary(), r2.summary(), "summary identical");
        assertEquals(r1.timeline().size(), r2.timeline().size(), "timeline size identical");
    }

    /**
     * and assert no outlier (total goals > 5x total xG). The original
     * smoke failure was A1: xG 1.777 → 0-7 (7 goals from 1.777 xG is
     * ≈3.9x — borderline; with the new divergence instrumentation we
     * want a hard guard).
     *
     * warn when the heuristic is breached; this test surfaces that as
     * a hard failure.
     */
    @Test
    void noMatchHasGoalsGreaterThan5xXg() {
        MatchContext ctx = buildContext("xg-outlier-1", 75, 75);
        DetailedMatchEngine engine = new DetailedMatchEngine();

        for (int seed = 1; seed <= 10; seed++) {
            DetailedMatchResult r = engine.simulate(ctx, seed);
            int totalGoals = r.homeGoals() + r.awayGoals();
            double totalXg = r.homeXg() + r.awayXg();

            // Sanity: goals >= 0 and xG >= 0 (always true by construction)
            assertTrue(totalGoals >= 0, "goals must be non-negative (seed=" + seed + ")");
            assertTrue(totalXg >= 0, "xG must be non-negative (seed=" + seed + ")");

            // Heuristic: no match has goals > 5x xG. If a match has 0
            // xG AND >0 goals, also flag (impossible unless bug).
            if (totalXg > 0) {
                double ratio = (double) totalGoals / totalXg;
                assertTrue(ratio <= 5.0,
                    "BUG #4: seed=" + seed + " has goals=" + totalGoals
                    + " xG=" + String.format("%.3f", totalXg)
                    + " ratio=" + String.format("%.2f", ratio)
                    + " (>5x outlier, possible xG/goals divergence)");
            } else if (totalGoals > 0) {
                fail("BUG #4: seed=" + seed + " has goals=" + totalGoals
                    + " but xG=0 (impossible — every goal should have xG from a shot)");
            }
        }
    }

    // ========== Fixture helpers ==========

    private MatchContext buildContext(String matchId, int homeOvr, int awayOvr) {
        List<SessionPlayer> homeStart = makePlayers("home", 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayers("away", 11, awayOvr);
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new MatchContext(
                matchId,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private List<SessionPlayer> makePlayers(String prefix, int count, int ovr) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + "_p" + i;
            SessionPlayer p = SessionPlayer.custom(
                    id, 25, "MID",
                    ovr, ovr, ovr, ovr, ovr, ovr,
                    BigDecimal.valueOf(ovr * 1000));
            list.add(p);
        }
        return list;
    }

    private SessionTeam makeTeam(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, "4-3-3", null);
    }
}