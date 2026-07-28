package com.footballmanager.application.service.simulation.detailed.stats;

/**
 *
 * for the queried career/season.
 */
public enum PlayerSeasonStatsDataCompleteness {
    /** All known completed matches for this season have detail data. */
    COMPLETE,
    /** Some rounds have detail data but gaps were detected. */
    PARTIAL,
    EMPTY,
    /** Cannot determine completeness from available data. */
    UNKNOWN
}
