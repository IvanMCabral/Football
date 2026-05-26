package com.footballmanager.application.service.simulation.v24.stats;

/**
 * V24D6M7: Data completeness level for player season stats response.
 *
 * <p>Communicates the quality of underlying V24DetailedMatchData coverage
 * for the queried career/season.
 */
public enum PlayerSeasonStatsDataCompleteness {
    /** All known completed matches for this season have detail data. */
    COMPLETE,
    /** Some rounds have detail data but gaps were detected. */
    PARTIAL,
    /** No V24DetailedMatchData records found for this career/season. */
    EMPTY,
    /** Cannot determine completeness from available data. */
    UNKNOWN
}