package com.footballmanager.application.service.simulation.v24.stats;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * V24D6M4: Query service for player season stats.
 *
 * <p>Reads V24DetailedMatchData from storage, delegates aggregation to
 * PlayerSeasonStatsAggregator. No mutation, no Redis writes.
 *
 * <p>Feature-gated: returns empty/incomplete when
 * {@code app.simulation.v24.expose-detail-api=false} (no data accessible).
 */
@Service
public class PlayerSeasonStatsQueryService {

    private final V24DetailedMatchStoragePort storagePort;
    private final PlayerSeasonStatsAggregator aggregator;
    private final boolean apiEnabled;

    public PlayerSeasonStatsQueryService(
            V24DetailedMatchStoragePort storagePort,
            PlayerSeasonStatsAggregator aggregator,
            @Qualifier("v24DetailApiEnabled") boolean apiEnabled) {
        this.storagePort = storagePort;
        this.aggregator = aggregator;
        this.apiEnabled = apiEnabled;
    }

    /**
     * Get all player season stats for a career/season.
     *
     * @param careerId  career identifier
     * @param season    season number
     * @return response with aggregated stats, or empty response if feature disabled or no data
     */
    public PlayerSeasonStatsResponse getPlayerSeasonStats(String careerId, Integer season) {
        return getPlayerSeasonStats(careerId, season, null, null, null);
    }

    /**
     * Get player season stats with filters.
     *
     * @param careerId  career identifier
     * @param season    season number
     * @param teamId    optional team filter
     * @param playerId  optional player filter
     * @param sortOptions optional sort/limit options (null = defaults)
     * @return aggregated stats filtered and sorted
     */
    public PlayerSeasonStatsResponse getPlayerSeasonStats(
            String careerId,
            Integer season,
            String teamId,
            String playerId,
            PlayerSeasonStatsAggregator.SortOptions sortOptions) {

        Objects.requireNonNull(careerId, "careerId must not be null");
        Objects.requireNonNull(season, "season must not be null");

        if (!apiEnabled) {
            return PlayerSeasonStatsResponse.builder()
                    .careerId(careerId)
                    .season(season)
                    .incomplete(true)
                    .message("Player season stats require V24 detail API to be enabled")
                    .build();
        }

        List<V24DetailedMatchData> details = storagePort.findByCareerId(careerId);

        // Filter by season in-memory
        List<V24DetailedMatchData> seasonDetails = details.stream()
                .filter(d -> d.seasonNumber() != null && d.seasonNumber().equals(season))
                .collect(Collectors.toList());

        if (seasonDetails.isEmpty()) {
            return PlayerSeasonStatsResponse.builder()
                    .careerId(careerId)
                    .season(season)
                    .playerStats(List.of())
                    .totalGoals(0)
                    .totalAssists(0)
                    .totalAppearances(0)
                    .averageRating(0.0)
                    .incomplete(false)
                    .message("No V24 detail data found for career " + careerId + " season " + season)
                    .build();
        }

        PlayerSeasonStatsAggregator.FilterOptions filterOpts =
                new PlayerSeasonStatsAggregator.FilterOptions(teamId, playerId);

        PlayerSeasonStatsResponse response = aggregator.aggregate(
                seasonDetails, careerId, season, filterOpts, sortOptions);

        // Mark incomplete if some career rounds may be missing (future: add round count check)
        boolean mayBeIncomplete = false; // future: compare expected rounds vs actual
        if (mayBeIncomplete) {
            response = PlayerSeasonStatsResponse.builder()
                    .careerId(response.careerId())
                    .season(response.season())
                    .playerStats(response.playerStats())
                    .totalGoals(response.totalGoals())
                    .totalAssists(response.totalAssists())
                    .totalAppearances(response.totalAppearances())
                    .averageRating(response.averageRating())
                    .incomplete(true)
                    .message("Season stats may be incomplete — some round data is missing")
                    .build();
        }

        return response;
    }

    /**
     * Check whether the stats API is enabled.
     */
    public boolean isApiEnabled() {
        return apiEnabled;
    }
}
