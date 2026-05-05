package com.footballmanager.domain.model.valueobject;

import com.footballmanager.application.service.domain.MatchQualityComputer;
import com.footballmanager.domain.model.aggregate.Team;

/**
 * Immutable record of match quality metrics computed from team OVR via Poisson lambda model.
 *
 * Phase 5A: Internal use only. Not persisted. Not exposed via API.
 *
 * xG values are derived from the same Poisson lambda formula used by MatchEngineImpl.
 * goalsToXgRatio is set separately via {@link #withGoals(int, int)}.
 */
public record MatchQualityMetrics(
    double homeXg,
    double awayXg,
    double totalXg,
    double goalsToXgRatio,
    double homeShare
) {
    /**
     * Creates MatchQualityMetrics from pre-computed lambdas.
     * goalsToXgRatio is set to 0.0 — use {@link #withGoals(int, int)} to update.
     */
    public static MatchQualityMetrics fromLambdas(MatchQualityComputer.MatchQualityLambdas lambdas) {
        validateFinite("homeXg", lambdas.homeLambda());
        validateFinite("awayXg", lambdas.awayLambda());
        validateFinite("homeShare", lambdas.homeShare());
        double total = lambdas.totalLambda();
        validateFinite("totalXg", total);
        validateNonNegative("homeXg", lambdas.homeLambda());
        validateNonNegative("awayXg", lambdas.awayLambda());
        return new MatchQualityMetrics(
            lambdas.homeLambda(),
            lambdas.awayLambda(),
            total,
            0.0,
            lambdas.homeShare()
        );
    }

    /**
     * Creates MatchQualityMetrics from Team aggregates.
     * Delegates to MatchQualityComputer.fromTeams() for lambda computation.
     */
    public static MatchQualityMetrics fromTeams(Team homeTeam, Team awayTeam) {
        MatchQualityComputer.MatchQualityLambdas lambdas =
            MatchQualityComputer.fromTeams(homeTeam, awayTeam);
        return fromLambdas(lambdas);
    }

    /**
     * Returns a new MatchQualityMetrics with goalsToXgRatio computed from actual goals.
     */
    public MatchQualityMetrics withGoals(int homeGoals, int awayGoals) {
        double ratio = totalXg > 0.0 ? (homeGoals + awayGoals) / totalXg : 0.0;
        validateFinite("goalsToXgRatio", ratio);
        return new MatchQualityMetrics(homeXg, awayXg, totalXg, ratio, homeShare);
    }

    private static void validateFinite(String field, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite, got: " + value);
        }
    }

    private static void validateNonNegative(String field, double value) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative, got: " + value);
        }
    }
}