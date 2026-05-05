package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.model.valueobject.PlayerId;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.model.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10A: Strength-aware simulation tests.
 * Validates that simulateWithStrength() behaves correctly and that
 * existing simulate() and simulateWithStyle() methods remain unchanged.
 */
class MatchEngineImplStrengthSimulationTest {

    private final MatchEngineImpl engine = new MatchEngineImpl();

    private Team createTeam(String name, int squadSize) {
        Team team = Team.create(
                TeamId.generate(),
                UserId.generate(),
                name,
                "England",
                new BigDecimal("10000000"),
                Formation.ofDefault());
        for (int i = 0; i < squadSize; i++) {
            team.addPlayer(PlayerId.generate());
        }
        return team;
    }

    // ========== Test 1: explicit OVR equals baseline when matching calculated OVR ==========

    @Test
    void explicitOvrEqualsBaselineWhenMatchingCalculatedOvr() {
        // 20 players → calculateTeamOverall = 70 + min(20, 20/2) = 70 + 10 = 80
        Team home = createTeam("Home", 20);
        Team away = createTeam("Away", 20);
        long seed = 12345L;

        MatchResult baseline = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult explicit = engine.simulateWithStrength(home, away, 80, 80, seed)
                .block(Duration.ofSeconds(5));

        assertNotNull(baseline);
        assertNotNull(explicit);

        // All match result fields identical
        assertEquals(baseline.getHomeGoals(), explicit.getHomeGoals(), "homeGoals");
        assertEquals(baseline.getAwayGoals(), explicit.getAwayGoals(), "awayGoals");
        assertEquals(baseline.getHomePossession(), explicit.getHomePossession(), "homePossession");
        assertEquals(baseline.getAwayPossession(), explicit.getAwayPossession(), "awayPossession");
        assertEquals(baseline.getHomeShots(), explicit.getHomeShots(), "homeShots");
        assertEquals(baseline.getAwayShots(), explicit.getAwayShots(), "awayShots");
        assertEquals(baseline.getSummary(), explicit.getSummary(), "summary");

        // Events identical
        assertEquals(baseline.getEvents().size(), explicit.getEvents().size());
        for (int i = 0; i < baseline.getEvents().size(); i++) {
            assertEquals(baseline.getEvents().get(i).getMinute(), explicit.getEvents().get(i).getMinute());
            assertEquals(baseline.getEvents().get(i).getEventType(), explicit.getEvents().get(i).getEventType());
            assertEquals(baseline.getEvents().get(i).getDescription(), explicit.getEvents().get(i).getDescription());
        }
    }

    // ========== Test 2: explicit OVR changes outcome deterministically ==========

    @Test
    void explicitOvrChangesOutcomeDeterministically() {
        Team home = createTeam("Home", 20);
        Team away = createTeam("Away", 20);
        long seed = 54321L;

        // Equal teams
        MatchResult r1a = engine.simulateWithStrength(home, away, 75, 75, seed)
                .block(Duration.ofSeconds(5));
        MatchResult r1b = engine.simulateWithStrength(home, away, 75, 75, seed)
                .block(Duration.ofSeconds(5));
        assertNotNull(r1a);
        assertNotNull(r1b);
        assertEquals(r1a.getHomeGoals(), r1b.getHomeGoals(), "75/75 deterministic");
        assertEquals(r1a.getSummary(), r1b.getSummary(), "75/75 summary identical");

        // Strong favorite — different outcome very likely
        MatchResult r2a = engine.simulateWithStrength(home, away, 90, 60, seed)
                .block(Duration.ofSeconds(5));
        MatchResult r2b = engine.simulateWithStrength(home, away, 90, 60, seed)
                .block(Duration.ofSeconds(5));
        assertNotNull(r2a);
        assertNotNull(r2b);
        assertEquals(r2a.getHomeGoals(), r2b.getHomeGoals(), "90/60 deterministic");
        assertEquals(r2a.getSummary(), r2b.getSummary(), "90/60 summary identical");

        // Verify equal teams give same home share vs asymmetric
        assertEquals(r1a.getHomePossession(), r1b.getHomePossession(), "75/75 possession deterministic");
        // Asymmetric should have different possession than equal
        assertTrue(r2a.getHomePossession() > r1a.getHomePossession(),
                "90/60 should give home higher possession than 75/75");
    }

    // ========== Test 3: invalid OVR falls back to baseline ==========

    @Test
    void invalidOvrFallsBackToBaseline() {
        Team home = createTeam("Home", 20);
        Team away = createTeam("Away", 20);
        long seed = 99999L;

        // OVR 80 is the calculated OVR for 20-player team
        MatchResult baseline = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));

        // Invalid values: 0, -1, 101, 999
        int[] invalidOvrs = {0, -1, 101, 999, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int invalid : invalidOvrs) {
            MatchResult fallback = engine.simulateWithStrength(home, away, invalid, invalid, seed)
                    .block(Duration.ofSeconds(5));
            assertNotNull(fallback, "fallback for invalid OVR " + invalid);
            assertEquals(baseline.getHomeGoals(), fallback.getHomeGoals(),
                    "homeGoals for invalid OVR " + invalid);
            assertEquals(baseline.getAwayGoals(), fallback.getAwayGoals(),
                    "awayGoals for invalid OVR " + invalid);
            assertEquals(baseline.getHomePossession(), fallback.getHomePossession(),
                    "homePossession for invalid OVR " + invalid);
            assertEquals(baseline.getSummary(), fallback.getSummary(),
                    "summary for invalid OVR " + invalid);
        }

        // Mixed: one valid, one invalid
        MatchResult mixed = engine.simulateWithStrength(home, away, 80, -1, seed)
                .block(Duration.ofSeconds(5));
        assertNotNull(mixed);
        // home OVR 80 valid → used; away OVR -1 invalid → falls back to 80
        assertEquals(baseline.getHomeGoals(), mixed.getHomeGoals());
        assertEquals(baseline.getAwayGoals(), mixed.getAwayGoals());
    }

    // ========== Test 4: explicit OVR produces valid results for representative scenarios ==========

    @Test
    void explicitOvrProducesValidResultsForRepresentativeScenarios() {
        Team home = createTeam("Home", 20);
        Team away = createTeam("Away", 20);
        int[][] scenarios = {
                {75, 75},
                {80, 70},
                {90, 60},
                {60, 90},
                {70, 70},
        };

        for (int[] scenario : scenarios) {
            int homeOvr = scenario[0];
            int awayOvr = scenario[1];
            long seed = 11111L + homeOvr * 1000 + awayOvr;

            MatchResult r = engine.simulateWithStrength(home, away, homeOvr, awayOvr, seed)
                    .block(Duration.ofSeconds(5));

            assertNotNull(r, homeOvr + "/" + awayOvr + " returned null");

            // Goals non-negative
            assertTrue(r.getHomeGoals() >= 0, homeOvr + "/" + awayOvr + " homeGoals < 0");
            assertTrue(r.getAwayGoals() >= 0, homeOvr + "/" + awayOvr + " awayGoals < 0");

            // Shots >= goals
            assertTrue(r.getHomeShots() >= r.getHomeGoals(),
                    homeOvr + "/" + awayOvr + " homeShots(" + r.getHomeShots() + ") < homeGoals(" + r.getHomeGoals() + ")");
            assertTrue(r.getAwayShots() >= r.getAwayGoals(),
                    homeOvr + "/" + awayOvr + " awayShots(" + r.getAwayShots() + ") < awayGoals(" + r.getAwayGoals() + ")");

            // Possession sums to 100
            assertEquals(100, r.getHomePossession() + r.getAwayPossession(),
                    homeOvr + "/" + awayOvr + " possession sum != 100");

            // Events consistent with score
            long homeGoals = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL && "HOME".equals(e.getDescription()))
                    .count();
            long awayGoals = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL && "AWAY".equals(e.getDescription()))
                    .count();
            assertEquals(r.getHomeGoals(), homeGoals, homeOvr + "/" + awayOvr + " home goal event count");
            assertEquals(r.getAwayGoals(), awayGoals, homeOvr + "/" + awayOvr + " away goal event count");

            // Summary contains score
            String expectedScore = r.getHomeGoals() + "-" + r.getAwayGoals();
            assertTrue(r.getSummary().contains(expectedScore),
                    homeOvr + "/" + awayOvr + " summary missing score: " + r.getSummary());
        }
    }

    // ========== Test 5: explicit OVR metrics remain bounded ==========

    @Test
    void explicitOvrMetricsRemainBounded() {
        Team home = createTeam("Home", 20);
        Team away = createTeam("Away", 20);

        int[][] scenarios = {{75, 75}, {80, 70}, {90, 60}};
        int matchesPerScenario = 1000;

        for (int[] scenario : scenarios) {
            int homeOvr = scenario[0];
            int awayOvr = scenario[1];
            int totalGoals = 0;
            int totalShots = 0;
            int zeroZeroCount = 0;
            int fourPlusCount = 0;

            for (int i = 0; i < matchesPerScenario; i++) {
                long seed = (long) (homeOvr * 10000 + awayOvr * 100 + i);
                MatchResult r = engine.simulateWithStrength(home, away, homeOvr, awayOvr, seed)
                        .block(Duration.ofSeconds(5));
                assertNotNull(r);
                totalGoals += r.getHomeGoals() + r.getAwayGoals();
                totalShots += r.getHomeShots() + r.getAwayShots();
                if (r.getHomeGoals() == 0 && r.getAwayGoals() == 0) zeroZeroCount++;
                if (r.getHomeGoals() + r.getAwayGoals() >= 4) fourPlusCount++;
            }

            double goalsPerMatch = (double) totalGoals / matchesPerScenario;
            double shotsPerMatch = (double) totalShots / matchesPerScenario;
            double zeroZeroRate = (double) zeroZeroCount / matchesPerScenario * 100;
            double fourPlusRate = (double) fourPlusCount / matchesPerScenario * 100;

            assertTrue(goalsPerMatch >= 2.0 && goalsPerMatch <= 3.8,
                    homeOvr + "/" + awayOvr + " goals/match " + goalsPerMatch + " out of [2.0, 3.8]");
            assertTrue(shotsPerMatch >= 8 && shotsPerMatch <= 20,
                    homeOvr + "/" + awayOvr + " shots/match " + shotsPerMatch + " out of [8, 20]");
            assertTrue(zeroZeroRate >= 3.0,
                    homeOvr + "/" + awayOvr + " 0-0 rate " + zeroZeroRate + "% < 3%");
            assertTrue(fourPlusRate <= 45.0,
                    homeOvr + "/" + awayOvr + " 4+ rate " + fourPlusRate + "% > 45%");
        }
    }

    // ========== Test 6: existing seeded simulation still deterministic ==========

    @Test
    void existingSeededSimulationStillDeterministic() {
        Team home = createTeam("Home", 18);
        Team away = createTeam("Away", 22);
        long seed = 77777L;

        MatchResult r1 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r2 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r3 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);

        assertEquals(r1.getHomeGoals(), r2.getHomeGoals());
        assertEquals(r1.getAwayGoals(), r2.getAwayGoals());
        assertEquals(r1.getHomePossession(), r2.getHomePossession());
        assertEquals(r1.getHomeShots(), r2.getHomeShots());
        assertEquals(r1.getSummary(), r2.getSummary());
        assertEquals(r1.getEvents().size(), r2.getEvents().size());

        assertEquals(r2.getHomeGoals(), r3.getHomeGoals());
        assertEquals(r2.getSummary(), r3.getSummary());
    }

    // ========== Test 7: simulateWithStyle still works ==========

    @Test
    void simulateWithStyleStillWorks() {
        Team home = createTeam("Home", 20);
        Team away = createTeam("Away", 20);
        long seed = 33333L;

        MatchResult r1 = engine.simulateWithStyle(home, away, TeamStyle.BALANCED, TeamStyle.BALANCED, seed)
                .block(Duration.ofSeconds(5));
        MatchResult r2 = engine.simulateWithStyle(home, away, TeamStyle.BALANCED, TeamStyle.BALANCED, seed)
                .block(Duration.ofSeconds(5));
        MatchResult baseline = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(baseline);

        // BALANCED/BALANCED = baseline
        assertEquals(baseline.getHomeGoals(), r1.getHomeGoals());
        assertEquals(baseline.getAwayGoals(), r1.getAwayGoals());
        assertEquals(baseline.getSummary(), r1.getSummary());

        // Deterministic
        assertEquals(r1.getHomeGoals(), r2.getHomeGoals());
        assertEquals(r1.getSummary(), r2.getSummary());
    }

    // ========== Test 8: asymmetric OVR scenarios ==========

    @Test
    void asymmetricOvrScenariosValid() {
        Team home = createTeam("Home", 20);
        Team away = createTeam("Away", 20);
        long seed = 22222L;

        // Strong home favorite
        MatchResult homeFav = engine.simulateWithStrength(home, away, 85, 65, seed)
                .block(Duration.ofSeconds(5));
        assertNotNull(homeFav);
        assertTrue(homeFav.getHomePossession() > 50,
                "home fav should have >50% possession");
        assertTrue(homeFav.getHomeGoals() >= 0);

        // Strong away favorite
        MatchResult awayFav = engine.simulateWithStrength(home, away, 65, 85, seed)
                .block(Duration.ofSeconds(5));
        assertNotNull(awayFav);
        assertTrue(awayFav.getAwayPossession() > 50,
                "away fav should have >50% possession");

        // Both valid
        assertEquals(100, homeFav.getHomePossession() + homeFav.getAwayPossession());
        assertEquals(100, awayFav.getHomePossession() + awayFav.getAwayPossession());
    }
}