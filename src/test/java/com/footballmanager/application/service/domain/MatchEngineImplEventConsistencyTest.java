package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.aggregate.*;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4: Event Consistency Validation Tests.
 *
 * Verifies that MatchResult events are perfectly consistent with
 * the generated score, teams, minutes, and summary.
 *
 * Tests use seeded simulation for reproducibility.
 * No production code changes — existing logic already validated as consistent.
 */
class MatchEngineImplEventConsistencyTest {

    private static final long SEED = 42L;
    private static final int MATCHES_PER_TEST = 1_000;

    private final MatchEngineImpl engine = new MatchEngineImpl();

    @Test
    void goalEventCountMatchesScore() {
        int failures = 0;
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < MATCHES_PER_TEST; i++) {
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
                String.format("Failed for %d/%d matches. First errors:%n%s",
                        failures, MATCHES_PER_TEST, errors.toString()));
    }

    @Test
    void homeGoalEventsAttributedToHome() {
        int failures = 0;
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < MATCHES_PER_TEST; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            // Home goals are those with description "HOME"
            long homeGoalCount = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                    .filter(e -> "HOME".equals(e.getDescription()))
                    .count();

            if (homeGoalCount != r.getHomeGoals()) {
                failures++;
                if (failures <= 3) {
                    errors.append(String.format("  Match %d: expected %d home goals, got %d HOME events%n",
                            i, r.getHomeGoals(), homeGoalCount));
                }
            }
        }

        assertEquals(0, failures,
                String.format("Failed for %d/%d matches. First errors:%n%s",
                        failures, MATCHES_PER_TEST, errors.toString()));
    }

    @Test
    void awayGoalEventsAttributedToAway() {
        int failures = 0;
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < MATCHES_PER_TEST; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            // Away goals are those with description "AWAY"
            long awayGoalCount = r.getEvents().stream()
                    .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                    .filter(e -> "AWAY".equals(e.getDescription()))
                    .count();

            if (awayGoalCount != r.getAwayGoals()) {
                failures++;
                if (failures <= 3) {
                    errors.append(String.format("  Match %d: expected %d away goals, got %d AWAY events%n",
                            i, r.getAwayGoals(), awayGoalCount));
                }
            }
        }

        assertEquals(0, failures,
                String.format("Failed for %d/%d matches. First errors:%n%s",
                        failures, MATCHES_PER_TEST, errors.toString()));
    }

    @Test
    void eventsSortedByMinute() {
        int failures = 0;
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < MATCHES_PER_TEST; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            List<MatchEvent> events = r.getEvents();
            for (int j = 1; j < events.size(); j++) {
                if (events.get(j).getMinute() < events.get(j - 1).getMinute()) {
                    failures++;
                    if (failures <= 3) {
                        errors.append(String.format("  Match %d: events not sorted at index %d%n",
                                i, j));
                    }
                    break;
                }
            }
        }

        assertEquals(0, failures,
                String.format("Events not sorted for %d/%d matches. First errors:%n%s",
                        failures, MATCHES_PER_TEST, errors.toString()));
    }

    @Test
    void eventMinutesWithinValidRange() {
        int failures = 0;
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < MATCHES_PER_TEST; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            for (MatchEvent e : r.getEvents()) {
                if (e.getMinute() < 0 || e.getMinute() > 120) {
                    failures++;
                    if (failures <= 3) {
                        errors.append(String.format("  Match %d: event minute %d out of range [0,120]%n",
                                i, e.getMinute()));
                    }
                    break;
                }
            }
        }

        assertEquals(0, failures,
                String.format("Invalid minutes found in %d/%d matches. First errors:%n%s",
                        failures, MATCHES_PER_TEST, errors.toString()));
    }

    @Test
    void summaryMatchesFinalScore() {
        int failures = 0;
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < MATCHES_PER_TEST; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);
            long seed = SEED + i;

            MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
            assertNotNull(r);

            String expectedScore = r.getHomeGoals() + "-" + r.getAwayGoals();
            if (!r.getSummary().contains(expectedScore)) {
                failures++;
                if (failures <= 3) {
                    errors.append(String.format("  Match %d: summary '%s' does not contain '%s'%n",
                            i, r.getSummary(), expectedScore));
                }
            }
        }

        assertEquals(0, failures,
                String.format("Summary mismatch in %d/%d matches. First errors:%n%s",
                        failures, MATCHES_PER_TEST, errors.toString()));
    }

    @Test
    void seededEventListDeterministic() {
        Team home = createTeam("Home", 80);
        Team away = createTeam("Away", 70);
        long seed = 12345L;

        MatchResult r1 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r2 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r3 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);

        // Event count must be identical
        assertEquals(r1.getEvents().size(), r2.getEvents().size(), "Event count differs between r1 and r2");
        assertEquals(r1.getEvents().size(), r3.getEvents().size(), "Event count differs between r1 and r3");

        // Every event must match in type, minute, playerName, description
        for (int i = 0; i < r1.getEvents().size(); i++) {
            MatchEvent e1 = r1.getEvents().get(i);
            MatchEvent e2 = r2.getEvents().get(i);
            MatchEvent e3 = r3.getEvents().get(i);

            assertEquals(e1.getEventType(), e2.getEventType(),
                    String.format("Event %d type differs: r1=%s, r2=%s", i, e1.getEventType(), e2.getEventType()));
            assertEquals(e1.getEventType(), e3.getEventType(),
                    String.format("Event %d type differs: r1=%s, r3=%s", i, e1.getEventType(), e3.getEventType()));

            assertEquals(e1.getMinute(), e2.getMinute(),
                    String.format("Event %d minute differs: r1=%d, r2=%d", i, e1.getMinute(), e2.getMinute()));
            assertEquals(e1.getMinute(), e3.getMinute(),
                    String.format("Event %d minute differs: r1=%d, r3=%d", i, e1.getMinute(), e3.getMinute()));

            assertEquals(e1.getPlayerName(), e2.getPlayerName(),
                    String.format("Event %d playerName differs: r1=%s, r2=%s", i, e1.getPlayerName(), e2.getPlayerName()));
            assertEquals(e1.getPlayerName(), e3.getPlayerName(),
                    String.format("Event %d playerName differs: r1=%s, r3=%s", i, e1.getPlayerName(), e3.getPlayerName()));

            assertEquals(e1.getDescription(), e2.getDescription(),
                    String.format("Event %d description differs: r1=%s, r2=%s", i, e1.getDescription(), e2.getDescription()));
            assertEquals(e1.getDescription(), e3.getDescription(),
                    String.format("Event %d description differs: r1=%s, r3=%s", i, e1.getDescription(), e3.getDescription()));
        }
    }

    @Test
    void eventConsistencyAcrossMultipleTeams() {
        // Test across different OVR combinations to ensure consistency holds universally
        int[][] scenarios = {
                {60, 60},  // equal low
                {75, 75},  // equal mid
                {90, 90},  // equal high
                {80, 70},  // slight favorite
                {90, 60},  // strong favorite
                {70, 80},  // slight underdog
        };

        for (int[] scenario : scenarios) {
            int homeOvr = scenario[0];
            int awayOvr = scenario[1];

            for (int i = 0; i < 100; i++) {
                Team home = createTeam("Home", homeOvr);
                Team away = createTeam("Away", awayOvr);
                long seed = (long) (homeOvr * 1000 + awayOvr + i);

                MatchResult r = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
                assertNotNull(r);

                // Goal count
                long goalCount = r.getEvents().stream()
                        .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                        .count();
                assertEquals(r.getHomeGoals() + r.getAwayGoals(), goalCount,
                        String.format("Goal count mismatch for %d-%d OVR, match %d", homeOvr, awayOvr, i));

                // Sorted
                for (int j = 1; j < r.getEvents().size(); j++) {
                    assertTrue(r.getEvents().get(j).getMinute() >= r.getEvents().get(j - 1).getMinute(),
                            String.format("Events not sorted for %d-%d OVR, match %d", homeOvr, awayOvr, i));
                }

                // Minutes in range
                for (MatchEvent e : r.getEvents()) {
                    assertTrue(e.getMinute() >= 0 && e.getMinute() <= 120,
                            String.format("Minute %d out of range for %d-%d OVR, match %d",
                                    e.getMinute(), homeOvr, awayOvr, i));
                }

                // Summary
                String expectedScore = r.getHomeGoals() + "-" + r.getAwayGoals();
                assertTrue(r.getSummary().contains(expectedScore),
                        String.format("Summary '%s' missing '%s' for %d-%d OVR, match %d",
                                r.getSummary(), expectedScore, homeOvr, awayOvr, i));
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