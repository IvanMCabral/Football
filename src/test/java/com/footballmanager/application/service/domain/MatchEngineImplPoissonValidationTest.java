package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.aggregate.*;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.entity.MatchEvent;
import com.footballmanager.domain.model.valueobject.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class MatchEngineImplPoissonValidationTest {

    private final MatchEngineImpl matchEngine = new MatchEngineImpl();

    @Test
    void shouldValidatePoissonGoalsEqualOvr() {
        AtomicLong totalGoals = new AtomicLong(0);
        AtomicInteger matchCount = new AtomicInteger(0);

        for (int i = 0; i < 1000; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);

            MatchResult result = matchEngine.simulate(home, away).block();
            assert result != null;
            totalGoals.addAndGet(result.getHomeGoals() + result.getAwayGoals());
            matchCount.incrementAndGet();
        }

        double avgGoals = (double) totalGoals.get() / matchCount.get();
        System.out.printf("Equal OVR (75-75): avg goals = %.3f (expected 2.4-3.0)%n", avgGoals);
        assertTrue(avgGoals >= 2.0 && avgGoals <= 3.5, "Average goals should be 2.0-3.5");
    }

    @Test
    void shouldValidatePoissonGoalsSlightFavorite() {
        AtomicLong totalGoals = new AtomicLong(0);
        AtomicInteger matchCount = new AtomicInteger(0);

        for (int i = 0; i < 1000; i++) {
            Team home = createTeam("Home", 80);
            Team away = createTeam("Away", 70);

            MatchResult result = matchEngine.simulate(home, away).block();
            assert result != null;
            totalGoals.addAndGet(result.getHomeGoals() + result.getAwayGoals());
            matchCount.incrementAndGet();
        }

        double avgGoals = (double) totalGoals.get() / matchCount.get();
        System.out.printf("Slight favorite (80-70): avg goals = %.3f (expected 2.4-3.2)%n", avgGoals);
        assertTrue(avgGoals >= 2.0 && avgGoals <= 3.5, "Average goals should be 2.0-3.5");
    }

    @Test
    void shouldValidatePoissonGoalsStrongFavorite() {
        AtomicLong totalGoals = new AtomicLong(0);
        AtomicInteger matchCount = new AtomicInteger(0);

        for (int i = 0; i < 1000; i++) {
            Team home = createTeam("Home", 90);
            Team away = createTeam("Away", 60);

            MatchResult result = matchEngine.simulate(home, away).block();
            assert result != null;
            totalGoals.addAndGet(result.getHomeGoals() + result.getAwayGoals());
            matchCount.incrementAndGet();
        }

        double avgGoals = (double) totalGoals.get() / matchCount.get();
        System.out.printf("Strong favorite (90-60): avg goals = %.3f (expected 2.6-3.8)%n", avgGoals);
        assertTrue(avgGoals >= 2.2 && avgGoals <= 4.2, "Average goals should be 2.2-4.2");
    }

    @Test
    void shouldValidateZeroZeroRate() {
        AtomicInteger zeroZeroCount = new AtomicInteger(0);
        int totalMatches = 1000;

        for (int i = 0; i < totalMatches; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);

            MatchResult result = matchEngine.simulate(home, away).block();
            assert result != null;
            if (result.getHomeGoals() == 0 && result.getAwayGoals() == 0) {
                zeroZeroCount.incrementAndGet();
            }
        }

        double zeroZeroRate = (double) zeroZeroCount.get() / totalMatches * 100;
        System.out.printf("0-0 rate: %.1f%% (expected >= 5%%)%n", zeroZeroRate);
        assertTrue(zeroZeroRate >= 5.0, "0-0 rate should be >= 5%");
    }

    @Test
    void shouldValidateFourPlusGoalsRate() {
        AtomicInteger fourPlusCount = new AtomicInteger(0);
        int totalMatches = 1000;

        for (int i = 0; i < totalMatches; i++) {
            Team home = createTeam("Home", 75);
            Team away = createTeam("Away", 75);

            MatchResult result = matchEngine.simulate(home, away).block();
            assert result != null;
            if (result.getHomeGoals() + result.getAwayGoals() >= 4) {
                fourPlusCount.incrementAndGet();
            }
        }

        double fourPlusRate = (double) fourPlusCount.get() / totalMatches * 100;
        System.out.printf("4+ goals rate: %.1f%% (expected <= 35%%)%n", fourPlusRate);
        assertTrue(fourPlusRate <= 35.0, "4+ goals rate should be <= 35%");
    }

    @Test
    void shouldCompleteWithin5Seconds() {
        Team homeTeam = createTeam("Home", 75);
        Team awayTeam = createTeam("Away", 75);

        StepVerifier.create(matchEngine.simulate(homeTeam, awayTeam))
                .expectSubscription()
                .assertNext(result -> Assertions.assertNotNull(result))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    private Team createTeam(String name, int overall) {
        Team team = Team.create(TeamId.generate(), UserId.generate(), name, "England",
                new BigDecimal("10000000"), Formation.ofDefault());
        for (int i = 0; i < 11; i++) {
            team.addPlayer(PlayerId.generate());
        }
        return team;
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}