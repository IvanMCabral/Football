package com.footballmanager.application.service.simulation.detailed;

/**
 *
 * objects with deterministic timelines, isolated from random engine behavior.
 */
public interface DetailedMatchEngineProvider {

    /**
     * Simulate a match and return the detailed result.
     *
     * @param context the match context (home/away teams, starting XI, bench, formation, style)
     * @param seed    deterministic seed for reproducibility
     * @return the detailed match result with timeline events
     */
    DetailedMatchResult simulate(MatchContext context, long seed);
}
