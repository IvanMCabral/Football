package com.footballmanager.application.service.simulation.v24;

/**
 * V24B: Computes expected goals (xG) for a shot using multi-factor model.
 *
 * <p>Factors:
 * <ul>
 *   <li>Shot location (distance from goal line, angle)</li>
 *   <li>Shooter quality (attack attribute + form)</li>
 *   <li>Assist quality (technique of passer)</li>
 *   <li>Defensive pressure (opponent defense + mentality)</li>
 *   <li>Goalkeeper quality</li>
 *   <li>Team style modifier (attacking = higher, defensive = lower)</li>
 * </ul>
 *
 * <p>Output clamped to [0.01, 0.60] (V24D6U4 tuned from 0.80).
 */
public class V24ShotXgCalculator {

    private static final double MIN_XG = 0.01;
    // V24D6U4: Reduced from 0.80 to 0.60 — even with multipliers, realistic xG
    // for a 6-yard box tap-in should not exceed ~0.50 in this tuned model.
    private static final double MAX_XG = 0.60;

    private static final double INSIDE_BOX_DISTANCE = 16.0; // meters from goal line
    private static final double SIX_YARD_BOX_DISTANCE = 8.0;

    public double calculateXg(V24ShotQuality quality, String formation) {
        double xg = baseXg(quality.location())
                * shooterMultiplier(quality.shooterQuality())
                * assistMultiplier(quality.assistQuality())
                * defensiveMultiplier(quality.defensivePressure())
                * goalkeeperMultiplier(quality.goalkeeperQuality())
                * styleMultiplier(quality.tacticModifier())
                * formationXgModifier(formation);

        return clamp(xg);
    }

    // V24D6U4: Base xG reduced ~45-55% to lower expected goals from ~4.5 to ~1.25 per team.
    // Target distribution: P(0)=29%, P(1)=36%, P(2)=22%, P(3+)=13% (Poisson λ=1.25).
    // Previous six-yard box was 0.38 → 0.20 (real football ~0.40-0.50 but with fewer chances).
    // Outside box reduced from 0.08 → 0.04, long range 0.04 → 0.02.
    private double baseXg(V24ShotLocation location) {
        return switch (location) {
            case SIX_YARD_BOX -> 0.20;
            case PENALTY_AREA_CENTER -> 0.12;
            case PENALTY_AREA_WIDE -> 0.09;
            case OUTSIDE_BOX -> 0.04;
            case LONG_RANGE -> 0.02;
        };
    }

    /**
     * V25D25: Formation-specific xG modifier (BUG_FORMATION_GOAL_NOOP fix).
     *
     * <p>Goal: make formation affect goal outcomes beyond the small
     * shot-location distribution shift that V24D23-A introduced. The
     * per-shot xG chain already includes shooter / assist / defense /
     * goalkeeper / style modifiers; adding the formation modifier as a
     * final multiplicative term amplifies the inter-formation xG delta
     * so it crosses the Bernoulli goal-noise floor (ΔxG formation ≥ 0.3
     * per team per match vs the ~0.05-0.13 from V24D23-A's location-only
     * approach, which was 1σ below the per-match Bernoulli noise of
     * ~1.28 stddev on goals).
     *
     * <p>Modifier values (v2, derived from tactical literature — see
     * MANAGER analisis-v25d25.md Section 3 Option (a)):
     * <ul>
     *   <li>4-4-2 baseline → 1.03 (forwards=2 only fires)</li>
     *   <li>4-3-3 → 1.10 (hasWingers)</li>
     *   <li>4-2-3-1 → 1.12 (forwards=1 + midfielders>=4)</li>
     *   <li>3-5-2 → 0.88 (defenders=3 -0.15, forwards=2 +0.03)</li>
     *   <li>5-3-2 → 0.78 (isBackFive -0.25, forwards=2 +0.03)</li>
     *   <li>3-4-3 → 1.07 (hasWingers +0.10, defenders=3 -0.15, forwards=1 +midfielders>=4 +0.12)</li>
     * </ul>
     *
     * <p>Edge: null/empty/unrecognized formation string → parser falls
     * back to 4-4-2 (BALANCED_DEFAULT) → modifier=1.03, deterministic.
     * Floor of 0.1 prevents any path from collapsing xG to zero.
     */
    private double formationXgModifier(String formation) {
        V24FormationParser parser = new V24FormationParser();
        V24FormationParser.V24Formation f = parser.parse(formation);
        double mod = 1.0;
        if (f.hasWingers()) mod += 0.10;       // 4-3-3, 3-4-3
        if (f.defenders() == 3) mod -= 0.15;   // 3-5-2, 3-4-3
        if (f.isBackFive()) mod -= 0.25;       // 5-3-2
        if (f.forwards() == 1 && f.midfielders() >= 4) mod += 0.12; // 4-2-3-1, 4-3-3
        if (f.forwards() == 2) mod += 0.03;    // 4-4-2, 3-5-2
        return Math.max(0.1, mod);
    }

    private double shooterMultiplier(double shooterQuality) {
        // shooterQuality is normalized [0, 1] from attack attribute (0-99) + form (0-100)
        // Base: 0.95 at average quality, scale up/down
        return 0.70 + (shooterQuality * 0.60);
    }

    private double assistMultiplier(double assistQuality) {
        // assistQuality normalized [0, 1]
        return 0.85 + (assistQuality * 0.30);
    }

    private double defensiveMultiplier(double defensivePressure) {
        // defensivePressure normalized [0, 1]: 0 = no pressure, 1 = maximum pressure
        // High pressure reduces xG significantly
        return Math.max(0.30, 1.10 - (defensivePressure * 0.80));
    }

    private double goalkeeperMultiplier(double goalkeeperQuality) {
        // goalkeeperQuality normalized [0, 1]
        return Math.max(0.50, 1.05 - (goalkeeperQuality * 0.55));
    }

    private double styleMultiplier(double tacticModifier) {
        // tacticModifier is [0.5, 1.5]
        // Map to: DEFENSIVE=0.85, BALANCED=1.00, ATTACKING=1.15
        // clamp to [0.5, 1.5]
        double m = Math.max(0.5, Math.min(1.5, tacticModifier));
        // Scale to meaningful multiplier range [0.85, 1.15]
        return 0.85 + (m - 0.5) * 0.30;
    }

    private double clamp(double xg) {
        if (xg < MIN_XG) return MIN_XG;
        if (xg > MAX_XG) return MAX_XG;
        return Math.round(xg * 1000.0) / 1000.0;
    }
}