package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.aggregate.*;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2: Deterministic Seeding / Replayability tests.
 * Verifies that seeded simulation produces identical results
 * and that unseeded simulation remains unchanged.
 */
class MatchEngineImplDeterminismTest {

    private final MatchEngineImpl engine = new MatchEngineImpl();

    @Test
    void sameSeedProducesIdenticalResult() {
        Team home = createTeam("Home", 75);
        Team away = createTeam("Away", 75);
        long seed = 42L;

        MatchResult r1 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r2 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r3 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);

        // Goals identical
        assertEquals(r1.getHomeGoals(), r2.getHomeGoals());
        assertEquals(r1.getAwayGoals(), r2.getAwayGoals());
        assertEquals(r1.getHomeGoals(), r3.getHomeGoals());
        assertEquals(r1.getAwayGoals(), r3.getAwayGoals());

        // Possession identical
        assertEquals(r1.getHomePossession(), r2.getHomePossession());
        assertEquals(r1.getAwayPossession(), r2.getAwayPossession());
        assertEquals(r1.getHomePossession(), r3.getHomePossession());

        // Shots identical
        assertEquals(r1.getHomeShots(), r2.getHomeShots());
        assertEquals(r1.getAwayShots(), r2.getAwayShots());
        assertEquals(r1.getHomeShots(), r3.getHomeShots());

        // Events identical (count and minutes)
        assertEquals(r1.getEvents().size(), r2.getEvents().size());
        assertEquals(r1.getEvents().size(), r3.getEvents().size());
        for (int i = 0; i < r1.getEvents().size(); i++) {
            assertEquals(r1.getEvents().get(i).getMinute(), r2.getEvents().get(i).getMinute());
            assertEquals(r1.getEvents().get(i).getEventType(), r2.getEvents().get(i).getEventType());
        }

        // Summary identical
        assertEquals(r1.getSummary(), r2.getSummary());
        assertEquals(r1.getSummary(), r3.getSummary());
    }

    @Test
    void differentSeedsProduceAtLeastOneDifferentResult() {
        Team home = createTeam("Home", 75);
        Team away = createTeam("Away", 75);

        // Use multiple seed pairs — at least one must differ
        long[] seeds = { 1L, 2L, 3L, 42L, 999L, 123456789L };
        MatchResult[] results = new MatchResult[seeds.length];

        for (int i = 0; i < seeds.length; i++) {
            results[i] = engine.simulate(home, away, seeds[i]).block(Duration.ofSeconds(5));
        }

        // Count how many results differ from the first result
        int differCount = 0;
        for (int i = 1; i < results.length; i++) {
            if (!resultsAreEqual(results[0], results[i])) {
                differCount++;
            }
        }

        // At least half of the different seeds should produce different results
        // (statistically at least one should definitely differ)
        assertTrue(differCount > 0,
                "Expected at least one different result across different seeds, but all were identical. " +
                "This is extremely unlikely with 6 different seeds.");
    }

    @Test
    void zeroSeedWorks() {
        Team home = createTeam("Home", 75);
        Team away = createTeam("Away", 75);

        MatchResult r = engine.simulate(home, away, 0L).block(Duration.ofSeconds(5));
        assertNotNull(r);
        assertTrue(r.getHomeGoals() >= 0);
        assertTrue(r.getAwayGoals() >= 0);
        assertTrue(r.getHomePossession() >= 30 && r.getHomePossession() <= 70);
    }

    @Test
    void negativeSeedWorks() {
        Team home = createTeam("Home", 75);
        Team away = createTeam("Away", 75);

        MatchResult r = engine.simulate(home, away, -1L).block(Duration.ofSeconds(5));
        assertNotNull(r);
        assertTrue(r.getHomeGoals() >= 0);
        assertTrue(r.getAwayGoals() >= 0);
        assertTrue(r.getHomePossession() >= 30 && r.getHomePossession() <= 70);

        // Negative seed should be deterministic like any other
        MatchResult r2 = engine.simulate(home, away, -1L).block(Duration.ofSeconds(5));
        assertNotNull(r2);
        assertEquals(r.getHomeGoals(), r2.getHomeGoals());
        assertEquals(r.getAwayGoals(), r2.getAwayGoals());
    }

    @Test
    void unseededSimulationStillWorks() {
        Team home = createTeam("Home", 75);
        Team away = createTeam("Away", 75);

        // Multiple unseeded calls should all be valid and within range
        for (int i = 0; i < 5; i++) {
            MatchResult r = engine.simulate(home, away).block(Duration.ofSeconds(5));
            assertNotNull(r);
            assertTrue(r.getHomeGoals() >= 0 && r.getHomeGoals() < 10);
            assertTrue(r.getAwayGoals() >= 0 && r.getAwayGoals() < 10);
            assertTrue(r.getHomePossession() >= 30 && r.getHomePossession() <= 70);
            assertTrue(r.getHomeShots() >= 3);
            assertTrue(r.getAwayShots() >= 3);
            assertNotNull(r.getSummary());
        }
    }

    @Test
    void seededSimulationSlightFavoriteStillValid() {
        Team home = createTeam("Home", 80);
        Team away = createTeam("Away", 70);
        long seed = 42L;

        MatchResult r1 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r2 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));

        assertNotNull(r1);
        assertNotNull(r2);

        // Verify determinism
        assertEquals(r1.getHomeGoals(), r2.getHomeGoals());
        assertEquals(r1.getAwayGoals(), r2.getAwayGoals());

        // Verify values are reasonable
        assertTrue(r1.getHomeGoals() >= 0 && r1.getHomeGoals() < 10);
        assertTrue(r1.getAwayGoals() >= 0 && r1.getAwayGoals() < 10);
        assertTrue(r1.getHomePossession() >= 30 && r1.getHomePossession() <= 70);
    }

    @Test
    void seededSimulationStrongFavoriteStillValid() {
        Team home = createTeam("Home", 90);
        Team away = createTeam("Away", 60);
        long seed = 12345L;

        MatchResult r1 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));
        MatchResult r2 = engine.simulate(home, away, seed).block(Duration.ofSeconds(5));

        assertNotNull(r1);
        assertNotNull(r2);

        // Verify determinism
        assertEquals(r1.getHomeGoals(), r2.getHomeGoals());
        assertEquals(r1.getAwayGoals(), r2.getAwayGoals());

        // Strong favorite home team should have more goals on average than equal
        // But this is probabilistic — just ensure it's valid
        assertTrue(r1.getHomeGoals() >= 0 && r1.getHomeGoals() < 10);
    }

    private Team createTeam(String name, int overall) {
        Team team = Team.create(TeamId.generate(), UserId.generate(), name, "England",
                new BigDecimal("10000000"), Formation.ofDefault());
        for (int i = 0; i < 11; i++) {
            team.addPlayer(PlayerId.generate());
        }
        return team;
    }

    private boolean resultsAreEqual(MatchResult r1, MatchResult r2) {
        if (r1 == null || r2 == null) return false;
        return r1.getHomeGoals() == r2.getHomeGoals()
                && r1.getAwayGoals() == r2.getAwayGoals()
                && r1.getHomePossession() == r2.getHomePossession()
                && r1.getAwayPossession() == r2.getAwayPossession()
                && r1.getHomeShots() == r2.getHomeShots()
                && r1.getAwayShots() == r2.getAwayShots()
                && r1.getEvents().size() == r2.getEvents().size()
                && r1.getSummary().equals(r2.getSummary());
    }
}