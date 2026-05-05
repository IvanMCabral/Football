package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.aggregate.Team;

/**
 * Pure utility for computing Poisson match quality lambdas.
 *
 * Encapsulates the calibrated V23 lambda formula used by MatchEngineImpl.
 * No randomness, no side effects, no gameplay effect.
 *
 * Phase 1B: On-demand xG computation without changing MatchResult or persistence.
 */
public final class MatchQualityComputer {

    private MatchQualityComputer() {}

    /**
     * Compute lambdas from Team OVR values.
     */
    public static MatchQualityLambdas fromTeams(Team homeTeam, Team awayTeam) {
        int homeOvr = calculateOvr(homeTeam);
        int awayOvr = calculateOvr(awayTeam);
        return computeLambdas(homeOvr, awayOvr);
    }

    /**
     * Compute lambdas from OVR integers.
     * Uses the exact same formula as MatchEngineImpl.performSimulation().
     */
    public static MatchQualityLambdas computeLambdas(int homeOvr, int awayOvr) {
        int ovrDiff = homeOvr - awayOvr;

        double baseTotalLambda = 2.60;
        double imbalanceBoost = Math.abs(ovrDiff) * 0.012;
        double totalLambda = clamp(baseTotalLambda + imbalanceBoost, 2.3, 3.05);

        double homeBaseShare = 0.52;
        double strengthShift = ovrDiff / 220.0;
        double homeShare = clamp(homeBaseShare + strengthShift, 0.25, 0.75);

        double homeLambda = totalLambda * homeShare;
        double awayLambda = totalLambda * (1.0 - homeShare);

        return new MatchQualityLambdas(homeLambda, awayLambda, totalLambda, homeShare);
    }

    private static int calculateOvr(Team team) {
        return 70 + Math.min(20, team.getSquadSize() / 2);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Immutable record of match quality lambda values.
     * teamXG = teamLambda (by definition of Poisson expected goals).
     */
    public record MatchQualityLambdas(
            double homeLambda,
            double awayLambda,
            double totalLambda,
            double homeShare
    ) {
        public double totalXg() { return homeLambda + awayLambda; }
        public double homeXg()  { return homeLambda; }
        public double awayXg()  { return awayLambda; }
    }
}