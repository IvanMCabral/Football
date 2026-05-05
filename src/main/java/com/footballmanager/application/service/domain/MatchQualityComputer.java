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

    /**
     * Style-aware lambda computation.
     *
     * Applies small additive style adjustments before clamping.
     * BALANCED+BALANCED is guaranteed to equal computeLambdas(homeOvr, awayOvr).
     *
     * Phase 6A: MatchEngineImpl does not consume this overload.
     * This is an internal utility for analytics and future Phase 6B integration.
     */
    public static MatchQualityLambdas computeLambdas(int homeOvr, int awayOvr,
                                                      TeamStyle homeStyle, TeamStyle awayStyle) {
        int ovrDiff = homeOvr - awayOvr;

        double baseTotalLambda = 2.60;
        double imbalanceBoost = Math.abs(ovrDiff) * 0.012;
        double totalLambda = baseTotalLambda + imbalanceBoost;

        // Apply style adjustments before clamping
        totalLambda += styleLambdaAdjust(homeStyle, awayStyle);
        totalLambda = clamp(totalLambda, 2.3, 3.05);

        double homeBaseShare = 0.52;
        double strengthShift = ovrDiff / 220.0;
        double homeShare = homeBaseShare + strengthShift;

        // Apply style adjustments before clamping
        homeShare += styleShareAdjust(homeStyle, awayStyle);
        homeShare = clamp(homeShare, 0.25, 0.75);

        double homeLambda = totalLambda * homeShare;
        double awayLambda = totalLambda * (1.0 - homeShare);

        return new MatchQualityLambdas(homeLambda, awayLambda, totalLambda, homeShare);
    }

    // Style adjustment helpers — small additive effects before clamping

    private static double styleLambdaAdjust(TeamStyle home, TeamStyle away) {
        return (lambdaStyleBonus(home) + lambdaStyleBonus(away)) / 2.0;
    }

    private static double styleShareAdjust(TeamStyle home, TeamStyle away) {
        double homeAdj = switch (home) {
            case ATTACKING   -> +0.03;
            case DEFENSIVE   -> -0.02;
            case POSSESSION  -> +0.02;
            case COUNTER     -> -0.01;
            case BALANCED    ->  0.0;
        };
        double awayAdj = switch (away) {
            case ATTACKING   -> -0.03;
            case DEFENSIVE   -> +0.02;
            case POSSESSION  -> -0.02;
            case COUNTER     -> +0.01;
            case BALANCED    ->  0.0;
        };
        return homeAdj + awayAdj;
    }

    private static double lambdaStyleBonus(TeamStyle style) {
        return switch (style) {
            case ATTACKING  -> +0.10;
            case DEFENSIVE  -> -0.10;
            case COUNTER    -> -0.08;
            case POSSESSION -> -0.05;
            case BALANCED   ->  0.0;
        };
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