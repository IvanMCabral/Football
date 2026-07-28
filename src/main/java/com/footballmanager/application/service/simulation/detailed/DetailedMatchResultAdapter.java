package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.MatchFixture;

/**
 *
 * <p>Maps only the 6 aggregate fields (goals, possession, shots).
 * Discards timeline, xG, and summary — those remain internal to detailed match.
 *
 * <p>Not wired into any production flow.
 */
public class DetailedMatchResultAdapter {

    private DetailedMatchResultAdapter() {} // utility class

    public static MatchFixture.MatchResultData toMatchResultData(DetailedMatchResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        return new MatchFixture.MatchResultData(
                result.homeGoals(),
                result.awayGoals(),
                result.homePossession(),
                result.awayPossession(),
                result.homeShots(),
                result.awayShots()
        );
    }
}
