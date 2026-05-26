package com.footballmanager.application.service.simulation.v24.stats;

/**
 * V24D6M7: Warning codes for the player season stats API.
 *
 * <p>Each code identifies a specific condition that the client should be
 * aware of. Warnings are informational — they do not change HTTP status.
 */
public enum PlayerSeasonStatsWarningCode {
    /** Limit was greater than max and was clamped to 200. */
    LARGE_LIMIT_CLAMPED,
    /** appearances is approximate — substitute appearances may be undercounted. */
    APPROXIMATE_APPEARANCES,
    /** matchesMissedInjured/matchesMissedSuspended are approximated. */
    APPROXIMATE_MATCHES_MISSED,
    /** No detail data was found for this career/season. */
    NO_DETAIL_DATA,
    /** V24 detail API feature flag is disabled. */
    V24_DETAIL_DISABLED,
    /** Some rounds appear to have missing detail data. */
    PARTIAL_DETAIL_DATA,
    /** minutesPlayed is not available from source data. */
    DEFERRED_MINUTES_PLAYED,
    /** shotsOnTarget is not available from source data. */
    DEFERRED_SHOTS_ON_TARGET,
    /** Form and energy history not available from source data. */
    DEFERRED_FORM_ENERGY
}