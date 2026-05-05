package com.footballmanager.application.service;

import com.footballmanager.domain.model.*;
import com.footballmanager.domain.ports.out.MatchEngine;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@Component
public class MatchEngineImpl implements MatchEngine {

    @Override
    public Mono<MatchResult> simulate(Team homeTeam, Team awayTeam) {
        return Mono.fromCallable(() -> performSimulation(homeTeam, awayTeam))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private MatchResult performSimulation(Team homeTeam, Team awayTeam) {
        Random random = new Random();

        int homeOverall = calculateTeamOverall(homeTeam);
        int awayOverall = calculateTeamOverall(awayTeam);

        int homePossession = calculatePossession(homeOverall, awayOverall, random);
        int awayPossession = 100 - homePossession;

        int homeShots = Math.max(3, (homePossession / 15) + random.nextInt(5));
        int awayShots = Math.max(3, (awayPossession / 15) + random.nextInt(5));

        // V23 Poisson goal model
        int ovrDiff = homeOverall - awayOverall;
        double baseTotalLambda = 2.60;
        double imbalanceBoost = Math.abs(ovrDiff) * 0.012;
        double totalLambda = clamp(baseTotalLambda + imbalanceBoost, 2.3, 3.05);

        double homeBaseShare = 0.52;
        double strengthShift = ovrDiff / 220.0;
        double homeShare = clamp(homeBaseShare + strengthShift, 0.25, 0.75);

        double homeLambda = totalLambda * homeShare;
        double awayLambda = totalLambda * (1.0 - homeShare);

        int homeGoals = poissonSample(homeLambda, random);
        int awayGoals = poissonSample(awayLambda, random);

        List<MatchEvent> events = generateEvents(homeGoals, awayGoals, random);
        String summary = generateSummary(homeTeam.getName(), awayTeam.getName(),
                                        homeGoals, awayGoals, homePossession);

        return MatchResult.of(homeGoals, awayGoals, homePossession, awayPossession,
                            homeShots, awayShots, events, summary);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int poissonSample(double lambda, Random random) {
        if (lambda <= 0) return 0;
        if (lambda < 30) {
            double L = Math.exp(-lambda);
            double p = 1.0;
            int k = 0;
            do {
                k++;
                p *= random.nextDouble();
            } while (p > L);
            return k - 1;
        } else {
            double mean = lambda + 0.5;
            double variance = lambda;
            double u = random.nextGaussian() * Math.sqrt(variance) + mean;
            return Math.max(0, (int) Math.round(u));
        }
    }

    private int calculateTeamOverall(Team team) {
        // Simple calculation: base 70 + squad bonus
        // Each player adds a small amount, formation doesn't matter much here
        return 70 + Math.min(20, team.getSquadSize() / 2);
    }

    private int calculatePossession(int homeOverall, int awayOverall, Random random) {
        double homeStrength = homeOverall / 100.0;
        double awayStrength = awayOverall / 100.0;
        double totalStrength = homeStrength + awayStrength;

        int basePossession = (int) ((homeStrength / totalStrength) * 100);
        int variance = random.nextInt(21) - 10;

        return Math.max(30, Math.min(70, basePossession + variance));
    }

    private List<MatchEvent> generateEvents(int homeGoals, int awayGoals, Random random) {
        List<MatchEvent> events = new ArrayList<>();

        for (int i = 0; i < homeGoals; i++) {
            int minute = 10 + random.nextInt(80);
            events.add(MatchEvent.of(MatchEvent.EventType.GOAL, minute,
                                    "Home Team Scorer #" + (i + 1),
                                    "Goal scored by home team"));
        }

        for (int i = 0; i < awayGoals; i++) {
            int minute = 10 + random.nextInt(80);
            events.add(MatchEvent.of(MatchEvent.EventType.GOAL, minute,
                                    "Away Team Scorer #" + (i + 1),
                                    "Goal scored by away team"));
        }

        if (random.nextDouble() < 0.3) {
            int cardMinute = 20 + random.nextInt(60);
            events.add(MatchEvent.of(MatchEvent.EventType.CARD, cardMinute,
                                    "Player Name", "Yellow card"));
        }

        if (random.nextDouble() < 0.2) {
            int injuryMinute = 30 + random.nextInt(50);
            events.add(MatchEvent.of(MatchEvent.EventType.INJURY, injuryMinute,
                                    "Injured Player", "Player injured"));
        }

        events.sort(Comparator.comparingInt(MatchEvent::getMinute));
        return events;
    }

    private String generateSummary(String homeTeam, String awayTeam, int homeGoals,
                                   int awayGoals, int homePossession) {
        return String.format("%s %d-%d %s | Possession: %d%%--%d%%",
                homeTeam, homeGoals, awayGoals, awayTeam, homePossession, 100 - homePossession);
    }
}
