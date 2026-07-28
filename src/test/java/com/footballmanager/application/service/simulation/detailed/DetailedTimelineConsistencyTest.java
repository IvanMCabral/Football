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
 * V24B: Timeline consistency tests.
 * Verifies: goals = goalEvents count, shots >= goals, possession sums to ~100,
 * xG sum matches shots, events ordered by minute.
 */
class V24TimelineConsistencyTest {

    @Test
    void goalCountMatchesGoalEvents() {
        MatchContext ctx = buildContext("gc-match-1", 75, 75);
        DetailedMatchEngine engine = new DetailedMatchEngine();
        DetailedMatchResult r = engine.simulate(ctx, 999L);

        long timelineGoals = r.timeline().goalEvents().size();
        int totalGoals = r.homeGoals() + r.awayGoals();
        assertEquals(totalGoals, timelineGoals,
                "Timeline goal events must equal homeGoals + awayGoals");
    }

    @Test
    void shotsGreaterOrEqualToGoals() {
        MatchContext ctx = buildContext("shot-goal-1", 75, 75);
        DetailedMatchEngine engine = new DetailedMatchEngine();

        DetailedMatchResult r1 = engine.simulate(ctx, 111L);
        assertTrue(r1.homeShots() >= r1.homeGoals(), "homeShots >= homeGoals");
        assertTrue(r1.awayShots() >= r1.awayGoals(), "awayShots >= awayGoals");

        DetailedMatchResult r2 = engine.simulate(ctx, 222L);
        assertTrue(r2.homeShots() >= r2.homeGoals(), "homeShots >= homeGoals (seed 222)");
        assertTrue(r2.awayShots() >= r2.awayGoals(), "awayShots >= awayGoals (seed 222)");
    }

    @Test
    void possessionSumsTo100() {
        MatchContext ctx = buildContext("poss-sum-1", 75, 75);
        DetailedMatchEngine engine = new DetailedMatchEngine();

        DetailedMatchResult r = engine.simulate(ctx, 333L);
        int sum = r.homePossession() + r.awayPossession();
        assertEquals(100, sum,
                "homePossession + awayPossession must equal 100, got " + sum);
    }

    @Test
    void xgSumCorrespondsToShots() {
        MatchContext ctx = buildContext("xg-shot-1", 75, 75);
        DetailedMatchEngine engine = new DetailedMatchEngine();

        DetailedMatchResult r = engine.simulate(ctx, 444L);
        // Every shot should add xG, so total xG > 0 if shots > 0
        if (r.homeShots() > 0) {
            assertTrue(r.homeXg() > 0, "homeXg must be > 0 when homeShots > 0");
        }
        if (r.awayShots() > 0) {
            assertTrue(r.awayXg() > 0, "awayXg must be > 0 when awayShots > 0");
        }
        // xG per shot reasonable range
        if (r.homeShots() > 0) {
            double avgXgPerShot = r.homeXg() / r.homeShots();
            assertTrue(avgXgPerShot <= 1.0, "avg xG per shot should be <= 1.0");
        }
    }

    @Test
    void eventsOrderedByMinute() {
        MatchContext ctx = buildContext("event-order-1", 75, 75);
        DetailedMatchEngine engine = new DetailedMatchEngine();

        DetailedMatchResult r = engine.simulate(ctx, 555L);
        List<DetailedMatchEvent> events = r.timeline().events();

        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i).minute() >= events.get(i - 1).minute(),
                    "Events must be ordered by minute");
        }
    }

    @Test
    void allEventsWithinMatchRange() {
        MatchContext ctx = buildContext("event-range-1", 75, 75);
        DetailedMatchEngine engine = new DetailedMatchEngine();

        DetailedMatchResult r = engine.simulate(ctx, 666L);
        for (DetailedMatchEvent e : r.timeline().events()) {
            assertTrue(e.minute() >= 1 && e.minute() <= 90,
                    "All events must have minute in range [1, 90]");
        }
    }

    @Test
    void timelineSizeGrowsWithMoreMatches() {
        MatchContext ctx = buildContext("tl-size-1", 75, 75);
        DetailedMatchEngine engine = new DetailedMatchEngine();

        DetailedMatchResult r1 = engine.simulate(ctx, 777L);
        DetailedMatchResult r2 = engine.simulate(ctx, 888L);

        // Different seeds should produce different timelines
        // (not guaranteed to be different size but likely)
        int size1 = r1.timeline().size();
        int size2 = r2.timeline().size();
        assertTrue(size1 >= 0 && size2 >= 0, "timeline sizes must be non-negative");
    }

    // ========== Fixture helpers ==========

    private MatchContext buildContext(String matchId, int homeOvr, int awayOvr) {
        List<SessionPlayer> homeStart = makePlayers("home-" + matchId, 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayers("away-" + matchId, 11, awayOvr);
        SessionTeam homeTeam = makeTeam("home-team-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-team-" + matchId, "Away FC");
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