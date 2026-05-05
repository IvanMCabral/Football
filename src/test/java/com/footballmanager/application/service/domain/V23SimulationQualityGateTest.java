package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.aggregate.*;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V23 Phase 8: Full Simulation Quality Gate.
 *
 * Comprehensive regression test that combines all Phase 1-7 validation guarantees
 * into a single reproducible test suite. This is the required gate before any
 * Phase 5/6/tactics/API work or future simulation changes.
 *
 * All tests use seeded simulation for reproducibility.
 * No production code changes — purely validation infrastructure.
 */
class V23SimulationQualityGateTest {

    private static final long SEED = 42L;

    private final com.footballmanager.application.service.domain.MatchEngineImpl engine =
            new com.footballmanager.application.service.domain.MatchEngineImpl();

    private static final Pattern GOAL_PATTERN =
            Pattern.compile("^(HOME|AWAY)_(ST|RW|LW|AM|CM|DM|DF)_\\d+$");

    @Test
    void qualityGate_equalOvrMetrics() {
        var collector = new MatchMetricsCollector();
        for (int i = 0; i < 10_000; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            MatchResult r = engine.simulate(home, away, SEED + i).block(Duration.ofSeconds(5));
            assertNotNull(r);
            double[] lambdas = MatchMetricsCollector.computeLambdas(75, 75);
            collector.record(r, lambdas[0], lambdas[1], 75, 75);
        }

        collector.printReport("Equal OVR (75-75)");
        collector.assertWithinRanges("Equal OVR");

        double spm = collector.shotsPerMatch();
        double gpm = collector.goalsPerMatch();
        double goalsPerShot = gpm / spm;

        assertTrue(spm >= 9.0 && spm <= 18.0,
                String.format("shots/match %.2f expected 9-18", spm));
        assertTrue(goalsPerShot >= 0.10 && goalsPerShot <= 0.30,
                String.format("goals/shot %.4f expected 0.10-0.30", goalsPerShot));
    }

    @Test
    void qualityGate_slightFavoriteMetrics() {
        var collector = new MatchMetricsCollector();
        for (int i = 0; i < 10_000; i++) {
            Team home = createTeam("Home", 80);
            Team away = createTeam("Away", 70);
            MatchResult r = engine.simulate(home, away, SEED + 10000 + i).block(Duration.ofSeconds(5));
            assertNotNull(r);
            double[] lambdas = MatchMetricsCollector.computeLambdas(80, 70);
            collector.record(r, lambdas[0], lambdas[1], 80, 70);
        }

        collector.printReport("Slight favorite (80-70)");
        collector.assertWithinRanges("Slight favorite");

        double spm = collector.shotsPerMatch();
        double gpm = collector.goalsPerMatch();
        double goalsPerShot = gpm / spm;

        assertTrue(spm >= 9.0 && spm <= 18.0,
                String.format("shots/match %.2f expected 9-18", spm));
        assertTrue(goalsPerShot >= 0.10 && goalsPerShot <= 0.30,
                String.format("goals/shot %.4f expected 0.10-0.30", goalsPerShot));
    }

    @Test
    void qualityGate_strongFavoriteMetrics() {
        var collector = new MatchMetricsCollector();
        for (int i = 0; i < 10_000; i++) {
            Team home = createTeam("Home", 90);
            Team away = createTeam("Away", 60);
            MatchResult r = engine.simulate(home, away, SEED + 20000 + i).block(Duration.ofSeconds(5));
            assertNotNull(r);
            double[] lambdas = MatchMetricsCollector.computeLambdas(90, 60);
            collector.record(r, lambdas[0], lambdas[1], 90, 60);
        }

        collector.printReport("Strong favorite (90-60)");
        collector.assertWithinRanges("Strong favorite");

        double spm = collector.shotsPerMatch();
        double gpm = collector.goalsPerMatch();
        double goalsPerShot = gpm / spm;

        assertTrue(spm >= 9.0 && spm <= 18.0,
                String.format("shots/match %.2f expected 9-18", spm));
        assertTrue(goalsPerShot >= 0.10 && goalsPerShot <= 0.30,
                String.format("goals/shot %.4f expected 0.10-0.30", goalsPerShot));
    }

    @Test
    void qualityGate_determinism() {
        Team home = createTeam("Home", 80);
        Team away = createTeam("Away", 70);
        long seed = 12345L;

        MatchResult r1 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r2 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r3 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);

        // Goals
        assertEquals(r1.getHomeGoals(), r2.getHomeGoals(), "homeGoals differ");
        assertEquals(r1.getAwayGoals(), r2.getAwayGoals(), "awayGoals differ");
        assertEquals(r1.getHomeGoals(), r3.getHomeGoals(), "homeGoals differ (r3)");
        assertEquals(r1.getAwayGoals(), r3.getAwayGoals(), "awayGoals differ (r3)");

        // Possession
        assertEquals(r1.getHomePossession(), r2.getHomePossession(), "homePossession differs");
        assertEquals(r1.getAwayPossession(), r2.getAwayPossession(), "awayPossession differs");

        // Shots
        assertEquals(r1.getHomeShots(), r2.getHomeShots(), "homeShots differ");
        assertEquals(r1.getAwayShots(), r2.getAwayShots(), "awayShots differ");

        // Summary
        assertEquals(r1.getSummary(), r2.getSummary(), "summary differs");
        assertEquals(r1.getSummary(), r3.getSummary(), "summary differs (r3)");

        // Full event list
        assertEquals(r1.getEvents().size(), r2.getEvents().size(), "event count differs");
        for (int i = 0; i < r1.getEvents().size(); i++) {
            assertEquals(r1.getEvents().get(i).getEventType(), r2.getEvents().get(i).getEventType(),
                    "event " + i + " type differs");
            assertEquals(r1.getEvents().get(i).getMinute(), r2.getEvents().get(i).getMinute(),
                    "event " + i + " minute differs");
            assertEquals(r1.getEvents().get(i).getPlayerName(), r2.getEvents().get(i).getPlayerName(),
                    "event " + i + " playerName differs");
            assertEquals(r1.getEvents().get(i).getDescription(), r2.getEvents().get(i).getDescription(),
                    "event " + i + " description differs");
        }
    }

    @Test
    void qualityGate_eventConsistency() {
        int failures = 0;
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < 1_000; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + 30000 + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            // Goal count matches score
            long goalCount = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                    .count();
            if (goalCount != r.getHomeGoals() + r.getAwayGoals()) {
                failures++;
                if (failures <= 3) errors.append(String.format("  [%d] goal count %d != score %d%n",
                        i, goalCount, r.getHomeGoals() + r.getAwayGoals()));
            }

            // HOME attribution
            long homeGoals2 = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                    .filter(e -> "HOME".equals(e.getDescription()))
                    .count();
            if (homeGoals2 != r.getHomeGoals()) {
                failures++;
                if (failures <= 3) errors.append(String.format("  [%d] HOME goals %d != homeGoals %d%n",
                        i, homeGoals2, r.getHomeGoals()));
            }

            // AWAY attribution
            long awayGoals2 = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                    .filter(e -> "AWAY".equals(e.getDescription()))
                    .count();
            if (awayGoals2 != r.getAwayGoals()) {
                failures++;
                if (failures <= 3) errors.append(String.format("  [%d] AWAY goals %d != awayGoals %d%n",
                        i, awayGoals2, r.getAwayGoals()));
            }

            // Sorted by minute
            for (int j = 1; j < r.getEvents().size(); j++) {
                if (r.getEvents().get(j).getMinute() < r.getEvents().get(j - 1).getMinute()) {
                    failures++;
                    if (failures <= 3) errors.append(String.format("  [%d] events not sorted at index %d%n", i, j));
                    break;
                }
            }

            // Minutes in range
            for (MatchEvent e : r.getEvents()) {
                if (e.getMinute() < 0 || e.getMinute() > 120) {
                    failures++;
                    if (failures <= 3) errors.append(String.format("  [%d] minute %d out of range%n",
                            i, e.getMinute()));
                    break;
                }
            }

            // Summary contains score
            String expectedScore = r.getHomeGoals() + "-" + r.getAwayGoals();
            if (!r.getSummary().contains(expectedScore)) {
                failures++;
                if (failures <= 3) errors.append(String.format("  [%d] summary '%s' missing '%s'%n",
                        i, r.getSummary(), expectedScore));
            }
        }

        assertEquals(0, failures,
                String.format("Event consistency failures: %d. First errors:%n%s", failures, errors));
    }

    @Test
    void qualityGate_roleDistribution() {
        AtomicLong totalGoals = new AtomicLong(0);
        AtomicLong attackerGoals = new AtomicLong(0);
        AtomicLong defensiveGoals = new AtomicLong(0);
        AtomicLong gkGoals = new AtomicLong(0);
        AtomicInteger invalidNames = new AtomicInteger(0);

        for (int i = 0; i < 10_000; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + 40000 + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            if (r == null) continue;

            for (MatchEvent e : r.getEvents()) {
                if (e.getEventType() != MatchEvent.EventType.GOAL) continue;

                String name = e.getPlayerName();
                if (!GOAL_PATTERN.matcher(name).matches()) {
                    invalidNames.incrementAndGet();
                }

                totalGoals.incrementAndGet();

                if (name.contains("_ST_") || name.contains("_RW_") || name.contains("_LW_") || name.contains("_AM_")) {
                    attackerGoals.incrementAndGet();
                } else if (name.contains("_DM_") || name.contains("_DF_")) {
                    defensiveGoals.incrementAndGet();
                } else if (name.contains("_GK_")) {
                    gkGoals.incrementAndGet();
                }
            }
        }

        double attackerShare = (double) attackerGoals.get() / totalGoals.get();
        double defensiveShare = (double) defensiveGoals.get() / totalGoals.get();

        assertEquals(0, invalidNames.get(),
                String.format("%d scorer names don't match role pattern", invalidNames.get()));
        assertTrue(attackerShare >= 0.70,
                String.format("Attacker share %.2f%% < 70%%", attackerShare * 100));
        assertTrue(defensiveShare <= 0.15,
                String.format("Defensive share %.2f%% > 15%%", defensiveShare * 100));
        assertEquals(0, gkGoals.get(),
                String.format("GK scored %d goals — should be 0", gkGoals.get()));
    }

    @Test
    void qualityGate_noImpossibleStats() {
        AtomicInteger failures = new AtomicInteger(0);
        var errors = new StringBuilder();

        for (int i = 0; i < 10_000; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + 50000 + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            if (r.getHomeGoals() > r.getHomeShots()) {
                failures.incrementAndGet();
                if (failures.get() <= 3) errors.append(String.format("  [%d] homeGoals %d > homeShots %d%n",
                        i, r.getHomeGoals(), r.getHomeShots()));
            }
            if (r.getAwayGoals() > r.getAwayShots()) {
                failures.incrementAndGet();
                if (failures.get() <= 3) errors.append(String.format("  [%d] awayGoals %d > awayShots %d%n",
                        i, r.getAwayGoals(), r.getAwayShots()));
            }
            if (r.getHomeShots() <= 0 || r.getAwayShots() <= 0) {
                failures.incrementAndGet();
                if (failures.get() <= 3) errors.append(String.format("  [%d] non-positive shots: home=%d away=%d%n",
                        i, r.getHomeShots(), r.getAwayShots()));
            }
            if (r.getHomePossession() + r.getAwayPossession() != 100) {
                failures.incrementAndGet();
                if (failures.get() <= 3) errors.append(String.format("  [%d] possession sum %d != 100%n",
                        i, r.getHomePossession() + r.getAwayPossession()));
            }
            if (r.getHomeGoals() < 0 || r.getAwayGoals() < 0) {
                failures.incrementAndGet();
                if (failures.get() <= 3) errors.append(String.format("  [%d] negative goals: home=%d away=%d%n",
                        i, r.getHomeGoals(), r.getAwayGoals()));
            }
        }

        assertEquals(0, failures.get(),
                String.format("Impossible stats found in %d matches. First errors:%n%s",
                        failures.get(), errors));
    }

    @Test
    void qualityGate_performanceSanity() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            MatchResult r = engine.simulate(home, away, SEED + 60000 + i).block(Duration.ofSeconds(5));
            assertNotNull(r);
        }
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 30_000,
                String.format("10,000 matches took %dms (>30,000ms threshold)", elapsed));
        System.out.printf("  Performance: 10,000 matches in %dms%n", elapsed);
    }

    private Team createTeam(String name, int ovr) {
        Team team = Team.create(TeamId.generate(), UserId.generate(), name, "England",
                new BigDecimal("10000000"), Formation.ofDefault());
        for (int i = 0; i < 11; i++) {
            team.addPlayer(PlayerId.generate());
        }
        return team;
    }
}