package com.footballmanager.application.service;

import com.footballmanager.domain.model.*;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MatchEngineImplTest {

    private final MatchEngineImpl matchEngine = new MatchEngineImpl();

    @Test
    void shouldSimulateMatch() {
        Team homeTeam = createTeam();
        Team awayTeam = createTeam();

        StepVerifier.create(matchEngine.simulate(homeTeam, awayTeam))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertTrue(result.getHomeGoals() >= 0);
                    assertTrue(result.getAwayGoals() >= 0);
                    assertTrue(result.getHomePossession() >= 30);
                    assertTrue(result.getHomePossession() <= 70);
                    assertTrue(result.getAwayPossession() >= 30);
                    assertTrue(result.getAwayPossession() <= 70);
                    assertEquals(100, result.getHomePossession() + result.getAwayPossession());
                    assertTrue(result.getHomeShots() >= 3);
                    assertTrue(result.getAwayShots() >= 3);
                    assertNotNull(result.getEvents());
                    assertNotNull(result.getSummary());
                })
                .verifyComplete();
    }

    @Test
    void shouldGenerateValidGoals() {
        for (int i = 0; i < 10; i++) {
            Team homeTeam = createTeam();
            Team awayTeam = createTeam();

            StepVerifier.create(matchEngine.simulate(homeTeam, awayTeam))
                    .assertNext(result -> {
                        assertTrue(result.getHomeGoals() >= 0);
                        assertTrue(result.getAwayGoals() >= 0);
                        assertTrue(result.getHomeGoals() < 10);
                        assertTrue(result.getAwayGoals() < 10);
                    })
                    .verifyComplete();
        }
    }

    @Test
    void shouldGenerateMatchEvents() {
        Team homeTeam = createTeam();
        Team awayTeam = createTeam();

        StepVerifier.create(matchEngine.simulate(homeTeam, awayTeam))
                .assertNext(result -> {
                    int goalCount = result.getHomeGoals() + result.getAwayGoals();
                    long goalEvents = result.getEvents().stream()
                            .filter(e -> e.getEventType() == MatchEvent.EventType.GOAL)
                            .count();

                    assertEquals(goalCount, goalEvents);

                    result.getEvents().forEach(event -> {
                        assertTrue(event.getMinute() >= 0);
                        assertTrue(event.getMinute() <= 120);
                        assertNotNull(event.getPlayerName());
                        assertNotNull(event.getDescription());
                    });
                })
                .verifyComplete();
    }

    @Test
    void shouldProduceValidSummary() {
        Team homeTeam = createTeam();
        homeTeam.addPlayer(PlayerId.generate());
        Team awayTeam = createTeam();

        StepVerifier.create(matchEngine.simulate(homeTeam, awayTeam))
                .assertNext(result -> {
                    String summary = result.getSummary();
                    assertNotNull(summary);
                    assertTrue(summary.contains(homeTeam.getName()));
                    assertTrue(summary.contains(awayTeam.getName()));
                    assertTrue(summary.contains(String.valueOf(result.getHomeGoals())));
                    assertTrue(summary.contains(String.valueOf(result.getAwayGoals())));
                })
                .verifyComplete();
    }

    @Test
    void shouldCompleteWithin5Seconds() {
        Team homeTeam = createTeam();
        Team awayTeam = createTeam();

        StepVerifier.create(matchEngine.simulate(homeTeam, awayTeam))
                .expectSubscription()
                .assertNext(result -> assertNotNull(result))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldHandleEmptySquads() {
        Team homeTeam = Team.create(TeamId.generate(), UserId.generate(), "Home", "England",
                new BigDecimal("10000000"), Formation.ofDefault());
        Team awayTeam = Team.create(TeamId.generate(), UserId.generate(), "Away", "England",
                new BigDecimal("10000000"), Formation.ofDefault());

        StepVerifier.create(matchEngine.simulate(homeTeam, awayTeam))
                .assertNext(result -> assertNotNull(result))
                .verifyComplete();
    }

    private Team createTeam() {
        Team team = Team.create(TeamId.generate(), UserId.generate(), "Test Team", "England",
                new BigDecimal("10000000"), Formation.ofDefault());

        for (int i = 0; i < 11; i++) {
            team.addPlayer(PlayerId.generate());
        }

        return team;
    }
}
