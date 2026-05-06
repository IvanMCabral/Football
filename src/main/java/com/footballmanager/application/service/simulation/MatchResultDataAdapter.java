package com.footballmanager.application.service.simulation;

import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.valueobject.MatchFixture;

/**
 * Maps MatchEngineImpl.MatchResult to MatchFixture.MatchResultData.
 * Events and summary from V23 engine are discarded — not persisted for AI matches.
 */
public final class MatchResultDataAdapter {

    private MatchResultDataAdapter() {} // utility class

    /**
     * Map all 6 fields from MatchResult to MatchResultData.
     * @param result from MatchEngineImpl (V23 engine)
     * @return MatchResultData for TournamentState persistence
     * @throws NullPointerException if result is null
     */
    public static MatchFixture.MatchResultData fromMatchResult(MatchResult result) {
        if (result == null) {
            throw new NullPointerException("MatchResult cannot be null");
        }
        return new MatchFixture.MatchResultData(
                result.getHomeGoals(),
                result.getAwayGoals(),
                result.getHomePossession(),
                result.getAwayPossession(),
                result.getHomeShots(),
                result.getAwayShots()
        );
    }
}