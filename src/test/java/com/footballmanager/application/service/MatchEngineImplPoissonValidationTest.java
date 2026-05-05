package com.footballmanager.application.service;

import com.footballmanager.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Post-implementation validation of Poisson goal model in MatchEngineImpl.
 * Uses actual production MatchEngineImpl path.
 */
class MatchEngineImplPoissonValidationTest {

    private final MatchEngineImpl matchEngine = new MatchEngineImpl();

    @Test
    void validatePoissonGoalModel_AllScenarios() {
        int MATCHES = 50_000;
        validateScenario("Equal Teams (equal OVR)", createTeamWithOvr(80), createTeamWithOvr(80), MATCHES);
        validateScenario("Slight Favorite (home stronger)", createTeamWithOvr(85), createTeamWithOvr(80), MATCHES);
        validateScenario("Strong Favorite (home dominant)", createTeamWithOvr(90), createTeamWithOvr(75), MATCHES);
    }

    private void validateScenario(String name, Team homeTeam, Team awayTeam, int MATCHES) {
        int[] totalGoalsArr = new int[MATCHES];
        int zeroZero = 0, draws = 0, homeWins = 0, awayWins = 0, fourPlus = 0;

        for (int m = 0; m < MATCHES; m++) {
            MatchResult result = matchEngine.simulate(homeTeam, awayTeam).block();
            assertNotNull(result);
            int hg = result.getHomeGoals();
            int ag = result.getAwayGoals();
            totalGoalsArr[m] = hg + ag;

            if (hg == 0 && ag == 0) zeroZero++;
            if (hg == ag) draws++;
            if (hg > ag) homeWins++;
            if (ag > hg) awayWins++;
            if (hg + ag >= 4) fourPlus++;
        }

        double avgGoals = 0;
        for (int g : totalGoalsArr) avgGoals += g;
        avgGoals /= MATCHES;

        double zeroZeroRate = 100.0 * zeroZero / MATCHES;
        double drawRate = 100.0 * draws / MATCHES;
        double homeWinRate = 100.0 * homeWins / MATCHES;
        double awayWinRate = 100.0 * awayWins / MATCHES;
        double fourPlusRate = 100.0 * fourPlus / MATCHES;

        System.out.println("\n=== " + name + " ===");
        System.out.printf("Goals/match: %.3f (target: 2.4-3.0)\n", avgGoals);
        System.out.printf("0-0 rate: %.2f%% (failure: <5%%)\n", zeroZeroRate);
        System.out.printf("4+ goals rate: %.2f%% (target: 12-30%%, strong favorite up to 32%%)\n", fourPlusRate);
        System.out.printf("Draw rate: %.2f%% (target: 18-30%%)\n", drawRate);
        System.out.printf("Home win rate: %.2f%%\n", homeWinRate);
        System.out.printf("Away win rate: %.2f%%\n", awayWinRate);

        assertTrue(avgGoals >= 2.4 && avgGoals <= 3.0,
            "goals/match " + avgGoals + " outside 2.4-3.0 range");
        assertTrue(zeroZeroRate >= 5.0,
            "0-0 rate " + zeroZeroRate + " below 5% failure threshold");
        assertTrue(fourPlusRate <= 32.0,
            "4+ goals rate " + fourPlusRate + " above 32%");
        assertTrue(drawRate >= 18.0 && drawRate <= 30.0,
            "draw rate " + drawRate + " outside 18-30% range");
    }

    private Team createTeamWithOvr(int targetOvr) {
        Team team = Team.create(TeamId.generate(), UserId.generate(), "Test Team", "England",
                new BigDecimal("10000000"), Formation.ofDefault());
        // squadSize/2 + 70 = targetOvr  => squadSize = (targetOvr - 70) * 2
        int squadSize = (targetOvr - 70) * 2;
        squadSize = Math.max(11, Math.min(30, squadSize)); // keep reasonable
        for (int i = 0; i < squadSize; i++) {
            team.addPlayer(PlayerId.generate());
        }
        return team;
    }
}