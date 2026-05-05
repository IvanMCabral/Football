package com.footballmanager.domain.service;

import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

public class V23GoalModelValidationTest {

    @Test
    void validatePoissonGoalModel_AllScenarios() {
        int MATCHES = 50_000;

        validateScenario("Equal (75 vs 75)", 75, 75, MATCHES);
        validateScenario("Slight Favorite (80 vs 75)", 80, 75, MATCHES);
        validateScenario("Strong Favorite (85 vs 70)", 85, 70, MATCHES);
    }

    private void validateScenario(String name, int homeOvr, int awayOvr, int MATCHES) {
        DefaultMatchSimulator sim = new DefaultMatchSimulator(new Random(42));
        int[] totalGoalsArr = new int[MATCHES];
        int zeroZero = 0, draws = 0, homeWins = 0, awayWins = 0, fourPlus = 0;

        for (int m = 0; m < MATCHES; m++) {
            MatchSimulator.MatchResult result = sim.simulateQuick("Home", "Away", homeOvr, awayOvr);
            int hg = result.homeGoals();
            int ag = result.awayGoals();
            totalGoalsArr[m] = hg + ag;
            if (hg == 0 && ag == 0) zeroZero++;
            if (hg == ag) draws++;
            if (hg > ag) homeWins++;
            if (ag > hg) awayWins++;
            if (totalGoalsArr[m] >= 4) fourPlus++;
        }

        double avgTotal = 0;
        for (int i = 0; i < MATCHES; i++) avgTotal += totalGoalsArr[i];
        avgTotal /= MATCHES;

        double goalsPerMatch = avgTotal;
        double zeroZeroRate = 100.0 * zeroZero / MATCHES;
        double fourPlusRate = 100.0 * fourPlus / MATCHES;
        double homeWinRate = 100.0 * homeWins / MATCHES;
        double awayWinRate = 100.0 * awayWins / MATCHES;
        double drawRate = 100.0 * draws / MATCHES;

        System.out.println("\n=== " + name + " ===");
        System.out.printf("Goals/match: %.3f (target: 2.4-3.0)%n", goalsPerMatch);
        System.out.printf("0-0 rate: %.2f%% (failure: <5%%)%n", zeroZeroRate);
        System.out.printf("4+ goals rate: %.2f%% (target: 12-30%%)%n", fourPlusRate);
        System.out.printf("Home win rate: %.2f%% (target: 35-55%%)%n", homeWinRate);
        System.out.printf("Away win rate: %.2f%% (target: 15-35%%)%n", awayWinRate);
        System.out.printf("Draw rate: %.2f%% (target: 18-30%%)%n", drawRate);

        assertTrue(goalsPerMatch >= 2.4 && goalsPerMatch <= 3.0,
            "goals/match " + goalsPerMatch + " outside 2.4-3.0 range");
        assertTrue(zeroZeroRate >= 5.0,
            "0-0 rate " + zeroZeroRate + " below 5% failure threshold");
        assertTrue(fourPlusRate <= 40.0,
            "4+ goals rate " + fourPlusRate + " above 40% failure threshold");
    }
}