package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.MatchFixture;

/**
 * V24A2: Isolated adapter from V24DetailedMatchResult to MatchFixture.MatchResultData.
 *
 * <p>Maps only the 6 aggregate fields (goals, possession, shots).
 * Discards timeline, xG, and summary — those remain internal to V24.
 *
 * <p>Not wired into any production flow.
 */
public class V24DetailedMatchResultAdapter {

    private V24DetailedMatchResultAdapter() {} // utility class

    public static MatchFixture.MatchResultData toMatchResultData(V24DetailedMatchResult result) {
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