package com.footballmanager.application.service.domain;

/**
 * Tactical style for match simulation.
 *
 * Phase 6A: Used only in MatchQualityComputer style-aware computeLambdas overload.
 * Internal utility — not persisted, not exposed via API.
 *
 * BALANCED produces exactly the same result as the no-style computeLambdas().
 * All other styles apply small adjustments clamped to [2.3, 3.05] totalLambda and [0.25, 0.75] homeShare.
 */
public enum TeamStyle {

    /** No modifier — produces exactly the same result as computeLambdas(int, int) */
    BALANCED,

    /** Slightly higher totalLambda, slight share boost */
    ATTACKING,

    /** Slightly lower totalLambda, slight defensive share effect */
    DEFENSIVE,

    /** Lower totalLambda, better chance share when weaker than opponent */
    COUNTER,

    /** Slightly lower totalLambda, slight possession share boost */
    POSSESSION
}
