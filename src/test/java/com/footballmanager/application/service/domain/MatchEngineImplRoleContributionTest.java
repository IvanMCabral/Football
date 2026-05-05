package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.aggregate.*;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V23 Phase 7: Player/Role Contribution Validation Tests.
 * Verifies that goal scorer names use synthetic role-based labels
 * and that role distribution matches expected weights.
 *
 * No production behavior changes — goal counts, attribution, and
 * event consistency are unchanged from Phase 4 validation.
 */
class MatchEngineImplRoleContributionTest {

    private static final long SEED = 42L;
    private static final int MATCHES = 10_000;

    private static final Pattern GOAL_PATTERN =
            Pattern.compile("^(HOME|AWAY)_(ST|RW|LW|AM|CM|DM|DF)_\\d+$");

    private final com.footballmanager.application.service.domain.MatchEngineImpl engine =
            new com.footballmanager.application.service.domain.MatchEngineImpl();

    @Test
    void goalScorersUseSyntheticRolePattern() {
        int failures = 0;
        var errors = new StringBuilder();

        for (int i = 0; i < MATCHES; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            for (MatchEvent e : r.getEvents()) {
                if (e.getEventType() == MatchEvent.EventType.GOAL) {
                    if (!GOAL_PATTERN.matcher(e.getPlayerName()).matches()) {
                        failures++;
                        if (failures <= 3) {
                            errors.append(String.format("  Match %d: invalid scorer name '%s'%n",
                                    i, e.getPlayerName()));
                        }
                    }
                }
            }
        }

        assertEquals(0, failures,
                String.format("Invalid scorer names in %d/%d matches. First errors:%n%s",
                        failures, MATCHES, errors.toString()));
    }

    @Test
    void goalEventCountStillMatchesScore() {
        int failures = 0;
        var errors = new StringBuilder();

        for (int i = 0; i < MATCHES; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            long goalCount = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                    .count();
            int expectedGoals = r.getHomeGoals() + r.getAwayGoals();

            if (goalCount != expectedGoals) {
                failures++;
                if (failures <= 3) {
                    errors.append(String.format("  Match %d: expected %d goals, got %d goal events%n",
                            i, expectedGoals, goalCount));
                }
            }
        }

        assertEquals(0, failures,
                String.format("Goal count mismatch in %d/%d matches. First errors:%n%s",
                        failures, MATCHES, errors.toString()));
    }

    @Test
    void homeAndAwayGoalAttributionStillCorrect() {
        int homeFailures = 0;
        int awayFailures = 0;
        var errors = new StringBuilder();

        for (int i = 0; i < MATCHES; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            long homeGoalCount = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                    .filter(e -> "HOME".equals(e.getDescription()))
                    .count();

            long awayGoalCount = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                    .filter(e -> "AWAY".equals(e.getDescription()))
                    .count();

            if (homeGoalCount != r.getHomeGoals()) {
                homeFailures++;
                if (homeFailures <= 3) {
                    errors.append(String.format("  Match %d: expected %d HOME goals, got %d HOME events%n",
                            i, r.getHomeGoals(), homeGoalCount));
                }
            }

            if (awayGoalCount != r.getAwayGoals()) {
                awayFailures++;
                if (awayFailures <= 3) {
                    errors.append(String.format("  Match %d: expected %d AWAY goals, got %d AWAY events%n",
                            i, r.getAwayGoals(), awayGoalCount));
                }
            }
        }

        assertEquals(0, homeFailures,
                String.format("HOME attribution mismatch in %d/%d matches. First errors:%n%s",
                        homeFailures, MATCHES, errors.toString()));
        assertEquals(0, awayFailures,
                String.format("AWAY attribution mismatch in %d/%d matches. First errors:%n%s",
                        awayFailures, MATCHES, errors.toString()));
    }

    @Test
    void attackerRolesDominateGoalDistribution() {
        AtomicLong totalGoals = new AtomicLong(0);
        AtomicLong attackerGoals = new AtomicLong(0);

        runScenario("Equal (75-75)", 75, 75, 10_000, totalGoals, attackerGoals, null, null, null);
        runScenario("Slight (80-70)", 80, 70, 10_000, totalGoals, attackerGoals, null, null, null);
        runScenario("Strong (90-60)", 90, 60, 10_000, totalGoals, attackerGoals, null, null, null);

        double attackerShare = (double) attackerGoals.get() / totalGoals.get();
        assertTrue(attackerShare >= 0.70,
                String.format("Attacker role share %.2f%% < 70%% threshold (got %d/%d goals)",
                        attackerShare * 100, attackerGoals.get(), totalGoals.get()));
    }

    @Test
    void defensiveRolesRemainLimited() {
        AtomicLong totalGoals = new AtomicLong(0);
        AtomicLong defensiveGoals = new AtomicLong(0);

        runScenario("Equal (75-75)", 75, 75, 10_000, totalGoals, null, defensiveGoals, null, null);
        runScenario("Slight (80-70)", 80, 70, 10_000, totalGoals, null, defensiveGoals, null, null);
        runScenario("Strong (90-60)", 90, 60, 10_000, totalGoals, null, defensiveGoals, null, null);

        double defensiveShare = (double) defensiveGoals.get() / totalGoals.get();
        assertTrue(defensiveShare <= 0.15,
                String.format("Defensive role share %.2f%% > 15%% threshold (got %d/%d goals)",
                        defensiveShare * 100, defensiveGoals.get(), totalGoals.get()));
    }

    @Test
    void goalkeeperNeverScores() {
        AtomicLong gkGoals = new AtomicLong(0);

        runScenario("Equal (75-75)", 75, 75, 10_000, null, null, null, null, gkGoals);
        runScenario("Slight (80-70)", 80, 70, 10_000, null, null, null, null, gkGoals);
        runScenario("Strong (90-60)", 90, 60, 10_000, null, null, null, null, gkGoals);

        assertEquals(0, gkGoals.get(),
                String.format("GK scored %d goals — GK weight should be 0%%", gkGoals.get()));
    }

    @Test
    void seededScorerNamesAreDeterministic() {
        Team home = createTeam("Home", 80);
        Team away = createTeam("Away", 70);
        long seed = 12345L;

        MatchResult r1 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r2 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r3 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);

        assertEquals(r1.getEvents().size(), r2.getEvents().size(),
                "Event count differs between r1 and r2");
        assertEquals(r1.getEvents().size(), r3.getEvents().size(),
                "Event count differs between r1 and r3");

        for (int i = 0; i < r1.getEvents().size(); i++) {
            String p1 = r1.getEvents().get(i).getPlayerName();
            String p2 = r2.getEvents().get(i).getPlayerName();
            String p3 = r3.getEvents().get(i).getPlayerName();

            assertEquals(p1, p2,
                    String.format("Event %d playerName differs: r1='%s', r2='%s'", i, p1, p2));
            assertEquals(p1, p3,
                    String.format("Event %d playerName differs: r1='%s', r3='%s'", i, p1, p3));
        }
    }

    private void runScenario(String name, int homeOvr, int awayOvr, int matches,
                             AtomicLong totalGoals, AtomicLong attackerGoals,
                             AtomicLong defensiveGoals, AtomicLong midfielderGoals,
                             AtomicLong gkGoals) {
        for (int i = 0; i < matches; i++) {
            Team home = createTeam("Home", homeOvr);
            Team away = createTeam("Away", awayOvr);
            long seed = SEED + homeOvr * 1000 + awayOvr + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            if (r == null) continue;

            for (MatchEvent e : r.getEvents()) {
                if (e.getEventType() != MatchEvent.EventType.GOAL) continue;

                String scorer = e.getPlayerName();
                if (totalGoals != null) totalGoals.incrementAndGet();

                if (scorer.contains("_ST_") || scorer.contains("_RW_") || scorer.contains("_LW_") || scorer.contains("_AM_")) {
                    if (attackerGoals != null) attackerGoals.incrementAndGet();
                } else if (scorer.contains("_DM_") || scorer.contains("_DF_")) {
                    if (defensiveGoals != null) defensiveGoals.incrementAndGet();
                } else if (scorer.contains("_CM_")) {
                    if (midfielderGoals != null) midfielderGoals.incrementAndGet();
                } else if (scorer.contains("_GK_")) {
                    if (gkGoals != null) gkGoals.incrementAndGet();
                }
            }
        }
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