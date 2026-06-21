package com.footballmanager.application.service.simulation.v24.stats;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6M3: Unit tests for PlayerSeasonStatsAggregator.
 * Deterministic, fast, no external I/O.
 */
class PlayerSeasonStatsAggregatorTest {

    private final PlayerSeasonStatsAggregator aggregator = new PlayerSeasonStatsAggregator();

    // ========================================================================
    // Empty / Null handling
    // ========================================================================

    @Test
    void emptyDetails_returnsEmptyResponse() {
        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(), "career1", 1);

        assertEquals("career1", resp.careerId());
        assertEquals(1, resp.season());
        assertTrue(resp.playerStats().isEmpty());
        assertEquals(0, resp.totalGoals());
        assertEquals(0, resp.totalAssists());
        assertFalse(resp.incomplete());
    }

    @Test
    void nullDetails_returnsEmptyResponse() {
        PlayerSeasonStatsResponse resp = aggregator.aggregate(null, "career1", 1);

        assertEquals("career1", resp.careerId());
        assertTrue(resp.playerStats().isEmpty());
        assertEquals(0, resp.totalGoals());
    }

    @Test
    void detailsWithOnlyNullEntries_returnsEmptyResponse() {
        List<V24DetailedMatchData> details = new ArrayList<>();
        details.add(null);
        details.add(null);

        PlayerSeasonStatsResponse resp = aggregator.aggregate(details, "career1", 1);

        assertTrue(resp.playerStats().isEmpty());
    }

    // ========================================================================
    // Career / Season filtering
    // ========================================================================

    @Test
    void filtersByCareerAndSeason() {
        V24DetailedMatchData match1 = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData match2 = makeDetail("career2", 1, "match2", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(match1, match2), "career1", 1);

        assertEquals(1, resp.playerStats().size());
        assertEquals("p1", resp.playerStats().get(0).playerId());
    }

    // ========================================================================
    // Deduplication
    // ========================================================================

    @Test
    void duplicateMatchId_notDoubleCounted() {
        V24DetailedMatchData match = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 2, 1, 5, 0, 0, 0, 0, 0, false, false)));

        // Same match submitted twice
        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(match, match), "career1", 1);

        assertEquals(1, resp.playerStats().size());
        assertEquals(2, resp.playerStats().get(0).goals());    // NOT 4
        assertEquals(1, resp.playerStats().get(0).assists());  // NOT 2
    }

    // ========================================================================
    // Core stat aggregation
    // ========================================================================

    @Test
    void goalsAndAssistsAggregatedCorrectly() {
        // m1: p1 scores 2 goals, 1 assist, 5 shots
        // m2: p1 scores 1 goal, 2 assists, 3 shots
        V24DetailedMatchData m1 = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 7.5, 2, 1, 5, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m2 = makeDetail("career1", 2, "match2", "teamA",
                List.of(makeRating("p1", "teamA", 6.5, 1, 2, 3, 0, 0, 0, 0, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(m1, m2), "career1", 1);

        assertEquals(1, resp.playerStats().size());
        PlayerSeasonStatsDto dto = resp.playerStats().get(0);
        assertEquals(3, dto.goals());    // 2 + 1
        assertEquals(3, dto.assists()); // 1 + 2
        assertEquals(8, dto.shots());    // 5 + 3
        assertEquals(2, dto.appearances());
    }

    @Test
    void cardsAndInjuriesAggregatedCorrectly() {
        // m1: 2 yellow, 0 red, 1 injury, 3 fouls, 0 keyPasses
        // m2: 1 yellow, 1 red, 0 injuries, 2 fouls, 0 keyPasses
        V24DetailedMatchData m1 = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 6.0, 0, 0, 0, 2, 0, 1, 3, 0, false, false)));
        V24DetailedMatchData m2 = makeDetail("career1", 2, "match2", "teamA",
                List.of(makeRating("p1", "teamA", 6.5, 0, 0, 0, 1, 1, 0, 2, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(m1, m2), "career1", 1);

        assertEquals(1, resp.playerStats().size());
        PlayerSeasonStatsDto dto = resp.playerStats().get(0);
        assertEquals(3, dto.yellowCards());  // 2 + 1
        assertEquals(1, dto.redCards());     // 0 + 1
        assertEquals(1, dto.injuries());      // 1 + 0
        assertEquals(5, dto.fouls());         // 3 + 2
    }

    @Test
    void foulsAndShotsAggregatedCorrectly() {
        // m1: 6 shots, 5 fouls, 1 goal, 0 assists
        // m2: 4 shots, 3 fouls, 2 goals, 1 assist
        V24DetailedMatchData m1 = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 1, 0, 6, 0, 0, 0, 5, 0, false, false)));
        V24DetailedMatchData m2 = makeDetail("career1", 2, "match2", "teamA",
                List.of(makeRating("p1", "teamA", 7.5, 2, 1, 4, 0, 0, 0, 3, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(m1, m2), "career1", 1);

        PlayerSeasonStatsDto dto = resp.playerStats().get(0);
        assertEquals(10, dto.shots());    // 6 + 4
        assertEquals(8, dto.fouls());     // 5 + 3
        assertEquals(3, dto.goals());      // 1 + 2
        assertEquals(1, dto.assists());    // 0 + 1
    }

    // ========================================================================
    // Rating computation
    // ========================================================================

    @Test
    void averageBestWorstRatingComputedCorrectly() {
        // Ratings: 8.0, 6.5, 7.5
        V24DetailedMatchData m1 = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 8.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m2 = makeDetail("career1", 2, "match2", "teamA",
                List.of(makeRating("p1", "teamA", 6.5, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m3 = makeDetail("career1", 3, "match3", "teamA",
                List.of(makeRating("p1", "teamA", 7.5, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(m1, m2, m3), "career1", 1);

        PlayerSeasonStatsDto dto = resp.playerStats().get(0);
        assertEquals(7.33, dto.averageRating(), 0.01);  // (8.0+6.5+7.5)/3
        assertEquals(8.0, dto.bestRating(), 0.01);
        assertEquals(6.5, dto.worstRating(), 0.01);
    }

    // ========================================================================
    // Appearances / starts
    // ========================================================================

    @Test
    void appearancesAndStartsDerivedFromSubstitutedInFlag() {
        // m1: started (substitutedIn=false)
        // m2: came on as sub (substitutedIn=true)
        // m3: started (substitutedIn=false)
        V24DetailedMatchData m1 = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m2 = makeDetail("career1", 2, "match2", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 0, 0, 0, 0, 0, 0, 0, 0, true, false)));
        V24DetailedMatchData m3 = makeDetail("career1", 3, "match3", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(m1, m2, m3), "career1", 1);

        PlayerSeasonStatsDto dto = resp.playerStats().get(0);
        assertEquals(3, dto.appearances());
        assertEquals(2, dto.starts()); // m1 + m3 started
    }

    // ========================================================================
    // Team isolation
    // ========================================================================

    @Test
    void teamIsolation_samePlayerDifferentTeamsCreatesSeparateRows() {
        // p1 plays for teamA (5 goals, 2 assists) and teamB (3 goals, 1 assist)
        V24DetailedMatchData m1 = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 5, 2, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m2 = makeDetail("career1", 2, "match2", "teamB",
                List.of(makeRating("p1", "teamB", 7.5, 3, 1, 0, 0, 0, 0, 0, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(m1, m2), "career1", 1);

        assertEquals(2, resp.playerStats().size());

        PlayerSeasonStatsDto teamA = resp.playerStats().stream()
                .filter(p -> "teamA".equals(p.teamId())).findFirst().orElseThrow();
        PlayerSeasonStatsDto teamB = resp.playerStats().stream()
                .filter(p -> "teamB".equals(p.teamId())).findFirst().orElseThrow();

        assertEquals(5, teamA.goals());
        assertEquals(3, teamB.goals());
    }

    // ========================================================================
    // Filters
    // ========================================================================

    @Test
    void playerFilter_returnsOnlyRequestedPlayer() {
        V24DetailedMatchData match = makeDetail("career1", 1, "match1", "teamA", List.of(
                makeRating("p1", "teamA", 7.0, 1, 0, 0, 0, 0, 0, 0, 0, false, false),
                makeRating("p2", "teamA", 7.0, 0, 1, 0, 0, 0, 0, 0, 0, false, false),
                makeRating("p3", "teamA", 7.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)
        ));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(
                List.of(match), "career1", 1,
                new PlayerSeasonStatsAggregator.FilterOptions(null, "p2"),
                new PlayerSeasonStatsAggregator.SortOptions(
                        PlayerSeasonStatsAggregator.SortField.GOALS,
                        PlayerSeasonStatsAggregator.SortOrder.DESC, 0, 0));

        assertEquals(1, resp.playerStats().size());
        assertEquals("p2", resp.playerStats().get(0).playerId());
    }

    @Test
    void teamFilter_returnsOnlyRequestedTeam() {
        V24DetailedMatchData m1 = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 1, 0, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m2 = makeDetail("career1", 2, "match2", "teamB",
                List.of(makeRating("p2", "teamB", 7.0, 2, 0, 0, 0, 0, 0, 0, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(
                List.of(m1, m2), "career1", 1,
                new PlayerSeasonStatsAggregator.FilterOptions("teamA", null),
                new PlayerSeasonStatsAggregator.SortOptions(
                        PlayerSeasonStatsAggregator.SortField.GOALS,
                        PlayerSeasonStatsAggregator.SortOrder.DESC, 0, 0));

        assertEquals(1, resp.playerStats().size());
        assertEquals("teamA", resp.playerStats().get(0).teamId());
        assertEquals(1, resp.playerStats().get(0).goals());
    }

    // ========================================================================
    // lastUpdatedRound
    // ========================================================================

    @Test
    void lastUpdatedRoundUsesMaxRound() {
        V24DetailedMatchData m5 = makeDetail("career1", 5, "match5", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m10 = makeDetail("career1", 10, "match10", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m3 = makeDetail("career1", 3, "match3", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(m5, m10, m3), "career1", 1);

        assertEquals(10, resp.playerStats().get(0).lastUpdatedRound());
    }

    // ========================================================================
    // Null safety
    // ========================================================================

    @Test
    void nullPlayerRatingsInDetail_handledSafely() {
        V24DetailedMatchData match = makeDetailWithNullRatings("career1", 1, "match1", "teamA");

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(match), "career1", 1);

        assertTrue(resp.playerStats().isEmpty());
    }

    @Test
    void nullRatingEntryInList_handledSafely() {
        List<V24PlayerMatchRatingDto> ratings = new ArrayList<>();
        ratings.add(null);
        ratings.add(makeRating("p1", "teamA", 7.0, 1, 0, 0, 0, 0, 0, 0, 0, false, false));
        ratings.add(null);

        V24DetailedMatchData match = makeDetailWithCustomRatings("career1", 1, "match1", "teamA", ratings);

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(match), "career1", 1);

        assertEquals(1, resp.playerStats().size());
        assertEquals(1, resp.playerStats().get(0).goals());
    }

    // ========================================================================
    // Sorting
    // ========================================================================

    @Test
    void sortingByGoalsDesc_putsHighestGoalsFirst() {
        // pA: 3 goals, pB: 1 goal, pC: 2 goals
        // Sorted desc: pA (3), pC (2), pB (1)
        V24DetailedMatchData m1 = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("pB", "teamA", 7.0, 1, 0, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m2 = makeDetail("career1", 2, "match2", "teamA",
                List.of(makeRating("pC", "teamA", 7.5, 2, 0, 0, 0, 0, 0, 0, 0, false, false)));
        V24DetailedMatchData m3 = makeDetail("career1", 3, "match3", "teamA",
                List.of(makeRating("pA", "teamA", 8.0, 3, 0, 0, 0, 0, 0, 0, 0, false, false)));

        // Use the new FilterOptions/SortOptions API
        PlayerSeasonStatsResponse resp = aggregator.aggregate(
                List.of(m1, m2, m3), "career1", 1,
                PlayerSeasonStatsAggregator.FilterOptions.NONE,
                new PlayerSeasonStatsAggregator.SortOptions(
                        PlayerSeasonStatsAggregator.SortField.GOALS,
                        PlayerSeasonStatsAggregator.SortOrder.DESC, 0, 0));

        List<PlayerSeasonStatsDto> list = resp.playerStats();
        assertEquals(3, list.size());
        // Sort desc by goals: pA (3), pC (2), pB (1)
        assertEquals("pA", list.get(0).playerId());
        assertEquals("pC", list.get(1).playerId());
        assertEquals("pB", list.get(2).playerId());
    }

    // ========================================================================
    // Approximate fields
    // ========================================================================

    @Test
    void approximateMissedMatchesDocumentedAndComputed() {
        // p1: 2 injuries, 5 yellows, 1 red card
        V24DetailedMatchData match = makeDetail("career1", 1, "match1", "teamA",
                List.of(makeRating("p1", "teamA", 7.0, 0, 0, 0, 5, 1, 2, 0, 0, false, false)));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(match), "career1", 1);

        PlayerSeasonStatsDto dto = resp.playerStats().get(0);
        // matchesMissedInjuredApprox = injuries × 2 = 2 × 2 = 4
        assertEquals(4, dto.matchesMissedInjuredApprox());
        // matchesMissedSuspendedApprox = redCards + floor(yellowCards / 5) = 1 + 1 = 2
        assertEquals(2, dto.matchesMissedSuspendedApprox());
    }

    // ========================================================================
    // Response totals
    // ========================================================================

    @Test
    void responseTotalsComputedCorrectly() {
        // m1: p1 (2G, 1A), p2 (0G)
        // m2: p1 (1G, 2A), p2 (0G)
        V24DetailedMatchData m1 = makeDetail("career1", 1, "match1", "teamA", List.of(
                makeRating("p1", "teamA", 7.0, 2, 1, 0, 0, 0, 0, 0, 0, false, false),
                makeRating("p2", "teamA", 6.5, 0, 0, 0, 0, 0, 0, 0, 0, false, false)
        ));
        V24DetailedMatchData m2 = makeDetail("career1", 2, "match2", "teamA", List.of(
                makeRating("p1", "teamA", 7.5, 1, 2, 0, 0, 0, 0, 0, 0, false, false),
                makeRating("p2", "teamA", 6.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false)
        ));

        PlayerSeasonStatsResponse resp = aggregator.aggregate(List.of(m1, m2), "career1", 1);

        assertEquals(3, resp.totalGoals());      // 2+1 = 3
        assertEquals(3, resp.totalAssists());    // 1+2 = 3
        assertEquals(4, resp.totalAppearances()); // p1: 2, p2: 2
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * makeRating args (in order):
     * playerId, teamId, rating, goals, assists, shots,
     * yellowCards, redCards, injuries, fouls, keyPasses,
     * substitutedIn, substitutedOut
     */
    private V24PlayerMatchRatingDto makeRating(
            String playerId, String teamId, double rating,
            int goals, int assists, int shots,
            int yellowCards, int redCards, int injuries, int fouls, int keyPasses,
            boolean substitutedIn, boolean substitutedOut) {
        return new V24PlayerMatchRatingDto(
                playerId, "Player " + playerId, teamId, "MID",
                rating,
                goals, assists, keyPasses, shots,
                yellowCards, redCards, injuries, fouls,
                substitutedIn, substitutedOut
        );
    }

    private V24DetailedMatchData makeDetail(
            String careerId, int round, String matchId, String homeTeamId,
            List<V24PlayerMatchRatingDto> playerRatings) {
        return new V24DetailedMatchData(
                matchId, careerId, 1, round,
                homeTeamId, "awayTeam",
                "Home", "Away",
                1, 0, 1.0, 0.5,
                5, 3, 55, 45,
                List.of(), playerRatings,
                "summary", "V24", 1, java.time.Instant.now(), null, null);
    }

    private V24DetailedMatchData makeDetailWithNullRatings(
            String careerId, int round, String matchId, String homeTeamId) {
        return new V24DetailedMatchData(
                matchId, careerId, 1, round,
                homeTeamId, "awayTeam",
                "Home", "Away",
                1, 0, 1.0, 0.5,
                5, 3, 55, 45,
                List.of(), null,
                "summary", "V24", 1, java.time.Instant.now(), null, null);
    }

    private V24DetailedMatchData makeDetailWithCustomRatings(
            String careerId, int round, String matchId, String homeTeamId,
            List<V24PlayerMatchRatingDto> playerRatings) {
        return new V24DetailedMatchData(
                matchId, careerId, 1, round,
                homeTeamId, "awayTeam",
                "Home", "Away",
                1, 0, 1.0, 0.5,
                5, 3, 55, 45,
                List.of(), playerRatings,
                "summary", "V24", 1, java.time.Instant.now(), null, null);
    }
}
