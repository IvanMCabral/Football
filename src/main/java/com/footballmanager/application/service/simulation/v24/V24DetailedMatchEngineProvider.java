package com.footballmanager.application.service.simulation.v24;

/**
 * V24D6D6B: Interface for V24DetailedMatchEngine to enable deterministic test injection.
 *
 * <p>The real V24DetailedMatchEngine implements this interface.
 * Tests can provide a fake/stub implementation that returns controlled V24DetailedMatchResult
 * objects with deterministic timelines, isolated from random engine behavior.
 */
public interface V24DetailedMatchEngineProvider {

    /**
     * Simulate a match and return the detailed result.
     *
     * @param context the match context (home/away teams, starting XI, bench, formation, style)
     * @param seed    deterministic seed for reproducibility
     * @return the detailed match result with timeline events
     */
    V24DetailedMatchResult simulate(V24MatchContext context, long seed);
}