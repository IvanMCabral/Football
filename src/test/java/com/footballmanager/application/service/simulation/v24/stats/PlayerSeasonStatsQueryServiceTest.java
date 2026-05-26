package com.footballmanager.application.service.simulation.v24.stats;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerSeasonStatsQueryServiceTest {

    @Mock
    private V24DetailedMatchStoragePort storagePort;

    @Mock
    private PlayerSeasonStatsAggregator aggregator;

    private PlayerSeasonStatsQueryService queryService;

    private static final String CAREER_ID = "career-123";
    private static final Integer SEASON = 1;

    @BeforeEach
    void setUp() {
        queryService = new PlayerSeasonStatsQueryService(storagePort, aggregator, true);
    }

    @Test
    void getPlayerSeasonStats_nullCareerId_throws() {
        assertThatThrownBy(() -> queryService.getPlayerSeasonStats(null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("careerId must not be null");
    }

    @Test
    void getPlayerSeasonStats_nullSeason_throws() {
        assertThatThrownBy(() -> queryService.getPlayerSeasonStats(CAREER_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("season must not be null");
    }

    @Test
    void getPlayerSeasonStats_apiDisabled_returnsIncomplete() {
        var disabledService = new PlayerSeasonStatsQueryService(storagePort, aggregator, false);

        PlayerSeasonStatsResponse response = disabledService.getPlayerSeasonStats(CAREER_ID, SEASON);

        assertThat(response.incomplete()).isTrue();
        assertThat(response.message()).contains("detail API");
        assertThat(response.careerId()).isEqualTo(CAREER_ID);
        assertThat(response.season()).isEqualTo(SEASON);
    }

    @Test
    void getPlayerSeasonStats_noData_returnsEmptyResponse() {
        when(storagePort.findByCareerId(CAREER_ID)).thenReturn(List.of());

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(CAREER_ID, SEASON);

        assertThat(response.playerStats()).isEmpty();
        assertThat(response.totalGoals()).isEqualTo(0);
        assertThat(response.incomplete()).isFalse();
        assertThat(response.message()).contains("No V24 detail data found");
    }

    @Test
    void getPlayerSeasonStats_withData_delegatesToAggregator() {
        V24DetailedMatchData match1 = makeDetail(CAREER_ID, 1, "match-1", "team-A", List.of(
                makeRating("p1", "team-A", 7.0, 1, 0, 3, 0, 0, 0, 0, 1, false, false),
                makeRating("p2", "team-A", 7.0, 0, 1, 2, 0, 0, 0, 0, 2, false, false)
        ));
        when(storagePort.findByCareerId(CAREER_ID)).thenReturn(List.of(match1));

        PlayerSeasonStatsResponse aggregatorResponse = PlayerSeasonStatsResponse.builder()
                .careerId(CAREER_ID)
                .season(SEASON)
                .playerStats(List.of())
                .totalGoals(1)
                .totalAssists(1)
                .totalAppearances(2)
                .averageRating(7.0)
                .incomplete(false)
                .message("ok")
                .build();
        when(aggregator.aggregateWithMetadata(any(), eq(CAREER_ID), eq(SEASON), any(), any()))
                .thenReturn(AggregationResult.builder()
                        .playerStats(List.of())
                        .totalGoals(1)
                        .totalAssists(1)
                        .totalAppearances(2)
                        .averageRating(7.0)
                        .matchIds(List.of("match-1"))
                        .totalMatchesProcessed(1)
                        .lastUpdatedRound(1)
                        .build());

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(CAREER_ID, SEASON);

        assertThat(response.totalGoals()).isEqualTo(1);
        assertThat(response.totalAssists()).isEqualTo(1);
        assertThat(response.incomplete()).isFalse();
    }

    @Test
    void getPlayerSeasonStats_filtersBySeason() {
        V24DetailedMatchData matchS1 = makeDetail(CAREER_ID, 1, "match-1", "team-A", List.of(
                makeRating("p1", "team-A", 7.0, 1, 0, 3, 0, 0, 0, 0, 1, false, false)
        ));
        V24DetailedMatchData matchS2 = makeDetail(CAREER_ID, 2, "match-2", "team-A", List.of(
                makeRating("p1", "team-A", 7.5, 2, 0, 4, 0, 0, 0, 0, 1, false, false)
        ));
        when(storagePort.findByCareerId(CAREER_ID)).thenReturn(List.of(matchS1, matchS2));

        PlayerSeasonStatsResponse aggregatorResponse = PlayerSeasonStatsResponse.builder()
                .careerId(CAREER_ID)
                .season(SEASON)
                .playerStats(List.of())
                .totalGoals(1)
                .totalAssists(0)
                .totalAppearances(1)
                .averageRating(7.0)
                .incomplete(false)
                .message("ok")
                .build();
        when(aggregator.aggregateWithMetadata(any(), eq(CAREER_ID), eq(1), any(), any()))
                .thenReturn(AggregationResult.builder()
                        .playerStats(List.of())
                        .totalGoals(1)
                        .totalAssists(0)
                        .totalAppearances(1)
                        .averageRating(7.0)
                        .matchIds(List.of("match-1"))
                        .totalMatchesProcessed(1)
                        .lastUpdatedRound(1)
                        .build());

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(CAREER_ID, 1);

        assertThat(response.season()).isEqualTo(1);
        assertThat(response.totalGoals()).isEqualTo(1);
    }

    @Test
    void getPlayerSeasonStats_withTeamFilter_passesToAggregator() {
        V24DetailedMatchData match1 = makeDetail(CAREER_ID, 1, "match-1", "team-A", List.of(
                makeRating("p1", "team-A", 7.0, 1, 0, 3, 0, 0, 0, 0, 1, false, false)
        ));
        when(storagePort.findByCareerId(CAREER_ID)).thenReturn(List.of(match1));

        PlayerSeasonStatsResponse aggregatorResponse = PlayerSeasonStatsResponse.builder()
                .careerId(CAREER_ID)
                .season(SEASON)
                .playerStats(List.of())
                .totalGoals(1)
                .totalAssists(0)
                .totalAppearances(1)
                .averageRating(7.0)
                .incomplete(false)
                .message("ok")
                .build();
        when(aggregator.aggregateWithMetadata(any(), eq(CAREER_ID), eq(SEASON), any(), any()))
                .thenReturn(AggregationResult.builder()
                        .playerStats(List.of())
                        .totalGoals(1)
                        .totalAssists(0)
                        .totalAppearances(1)
                        .averageRating(7.0)
                        .matchIds(List.of("match-1"))
                        .totalMatchesProcessed(1)
                        .lastUpdatedRound(1)
                        .build());

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(CAREER_ID, SEASON, "team-A", null);

        assertThat(response.totalGoals()).isEqualTo(1);
    }

    @Test
    void getPlayerSeasonStats_withPlayerFilter_passesToAggregator() {
        V24DetailedMatchData match1 = makeDetail(CAREER_ID, 1, "match-1", "team-A", List.of(
                makeRating("p1", "team-A", 7.0, 1, 0, 3, 0, 0, 0, 0, 1, false, false)
        ));
        when(storagePort.findByCareerId(CAREER_ID)).thenReturn(List.of(match1));

        PlayerSeasonStatsResponse aggregatorResponse = PlayerSeasonStatsResponse.builder()
                .careerId(CAREER_ID)
                .season(SEASON)
                .playerStats(List.of())
                .totalGoals(1)
                .totalAssists(0)
                .totalAppearances(1)
                .averageRating(7.0)
                .incomplete(false)
                .message("ok")
                .build();
        when(aggregator.aggregateWithMetadata(any(), eq(CAREER_ID), eq(SEASON), any(), any()))
                .thenReturn(AggregationResult.builder()
                        .playerStats(List.of())
                        .totalGoals(1)
                        .totalAssists(0)
                        .totalAppearances(1)
                        .averageRating(7.0)
                        .matchIds(List.of("match-1"))
                        .totalMatchesProcessed(1)
                        .lastUpdatedRound(1)
                        .build());

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(CAREER_ID, SEASON, null, "p1");

        assertThat(response.totalGoals()).isEqualTo(1);
    }

    @Test
    void getPlayerSeasonStats_withSortOptions_passesToAggregator() {
        V24DetailedMatchData match1 = makeDetail(CAREER_ID, 1, "match-1", "team-A", List.of(
                makeRating("p1", "team-A", 7.0, 1, 0, 3, 0, 0, 0, 0, 1, false, false)
        ));
        when(storagePort.findByCareerId(CAREER_ID)).thenReturn(List.of(match1));

        PlayerSeasonStatsAggregator.SortOptions sortOpts = new PlayerSeasonStatsAggregator.SortOptions(
                PlayerSeasonStatsAggregator.SortField.GOALS, PlayerSeasonStatsAggregator.SortOrder.DESC, 10, 0);

        when(aggregator.aggregateWithMetadata(any(), eq(CAREER_ID), eq(SEASON), any(), any()))
                .thenReturn(AggregationResult.builder()
                        .playerStats(List.of())
                        .totalGoals(1)
                        .totalAssists(0)
                        .totalAppearances(1)
                        .averageRating(7.0)
                        .matchIds(List.of("match-1"))
                        .totalMatchesProcessed(1)
                        .lastUpdatedRound(1)
                        .build());

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(CAREER_ID, SEASON, null, null, sortOpts);

        assertThat(response.totalGoals()).isEqualTo(1);
    }

    @Test
    void isApiEnabled_returnsConfiguredValue() {
        var enabledService = new PlayerSeasonStatsQueryService(storagePort, aggregator, true);
        var disabledService = new PlayerSeasonStatsQueryService(storagePort, aggregator, false);

        assertThat(enabledService.isApiEnabled()).isTrue();
        assertThat(disabledService.isApiEnabled()).isFalse();
    }

    @Test
    void getPlayerSeasonStats_metadataHasCorrectFields() {
        V24DetailedMatchData match1 = makeDetail(CAREER_ID, 1, "match-1", "team-A", List.of(
                makeRating("p1", "team-A", 7.0, 1, 0, 3, 0, 0, 0, 0, 1, false, false)
        ));
        when(storagePort.findByCareerId(CAREER_ID)).thenReturn(List.of(match1));

        when(aggregator.aggregateWithMetadata(any(), eq(CAREER_ID), eq(SEASON), any(), any()))
                .thenReturn(AggregationResult.builder()
                        .playerStats(List.of())
                        .totalGoals(1)
                        .totalAssists(0)
                        .totalAppearances(1)
                        .averageRating(7.0)
                        .matchIds(List.of("match-1"))
                        .totalMatchesProcessed(1)
                        .lastUpdatedRound(5)
                        .build());

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(CAREER_ID, SEASON);

        assertThat(response.metadata()).isNotNull();
        assertThat(response.metadata().dataSource()).isEqualTo("V24_DETAIL");
        assertThat(response.metadata().totalMatchesProcessed()).isEqualTo(1);
        assertThat(response.metadata().lastUpdatedRound()).isEqualTo(5);
        assertThat(response.metadata().versionHash()).isNotNull();
    }

    @Test
    void getPlayerSeasonStats_warningsPresentOnNonEmpty() {
        V24DetailedMatchData match1 = makeDetail(CAREER_ID, 1, "match-1", "team-A", List.of(
                makeRating("p1", "team-A", 7.0, 1, 0, 3, 0, 0, 0, 0, 1, false, false)
        ));
        when(storagePort.findByCareerId(CAREER_ID)).thenReturn(List.of(match1));

        when(aggregator.aggregateWithMetadata(any(), eq(CAREER_ID), eq(SEASON), any(), any()))
                .thenReturn(AggregationResult.builder()
                        .playerStats(List.of(
                                new PlayerSeasonStatsDto(CAREER_ID, SEASON, "team-A", "p1", "Player 1", "FWD",
                                        1, 1, 1, 0, 3, 3, 0, 0, 0, 0, 0, 0, 7.0, 7.0, 7.0, 1)))
                        .totalGoals(1)
                        .totalAssists(0)
                        .totalAppearances(1)
                        .averageRating(7.0)
                        .matchIds(List.of("match-1"))
                        .totalMatchesProcessed(1)
                        .lastUpdatedRound(1)
                        .build());

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(CAREER_ID, SEASON);

        assertThat(response.warnings()).isNotEmpty();
        boolean hasApproxAppearances = response.warnings().stream()
                .anyMatch(w -> w.code() == PlayerSeasonStatsWarningCode.APPROXIMATE_APPEARANCES);
        boolean hasApproxMissed = response.warnings().stream()
                .anyMatch(w -> w.code() == PlayerSeasonStatsWarningCode.APPROXIMATE_MATCHES_MISSED);
        assertThat(hasApproxAppearances).isTrue();
        assertThat(hasApproxMissed).isTrue();
    }

    // --- helpers (matching PlayerSeasonStatsAggregatorTest patterns) ---

    /**
     * makeDetail args: careerId, round, matchId, homeTeamId, playerRatings
     */
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
                "summary", "V24", 1, java.time.Instant.now()
        );
    }

    /**
     * makeRating args: playerId, teamId, rating, goals, assists, shots,
     * yellowCards, redCards, injuries, fouls, keyPasses, substitutedIn, substitutedOut
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
}