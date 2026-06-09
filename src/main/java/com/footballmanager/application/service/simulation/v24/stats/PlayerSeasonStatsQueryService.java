package com.footballmanager.application.service.simulation.v24.stats;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * V24D6M7: Query service for player season stats.
 *
 * <p>Reads V24DetailedMatchData from storage, delegates aggregation to
 * PlayerSeasonStatsAggregator. No mutation, no Redis writes.
 *
 * <p>Feature-gated: returns empty/incomplete when
 * {@code app.simulation.v24.expose-detail-api=false} (no data accessible).
 */
@Slf4j
@Service
public class PlayerSeasonStatsQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

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
     */
    public PlayerSeasonStatsResponse getPlayerSeasonStats(String careerId, Integer season) {
        return getPlayerSeasonStats(careerId, season, null, null,
                DEFAULT_LIMIT, 0, null, null);
    }

    /**
     * Get player season stats with optional team/player filter.
     */
    public PlayerSeasonStatsResponse getPlayerSeasonStats(
            String careerId,
            Integer season,
            String teamId,
            String playerId) {
        return getPlayerSeasonStats(careerId, season, teamId, playerId,
                DEFAULT_LIMIT, 0, null, null);
    }

    /**
     * Get player season stats with sort options (backward-compatible overload).
     */
    public PlayerSeasonStatsResponse getPlayerSeasonStats(
            String careerId,
            Integer season,
            String teamId,
            String playerId,
            PlayerSeasonStatsAggregator.SortOptions sortOptions) {
        if (sortOptions == null) {
            return getPlayerSeasonStats(careerId, season, teamId, playerId);
        }
        return getPlayerSeasonStats(careerId, season, teamId, playerId,
                sortOptions.limit, sortOptions.offset,
                sortOptions.sortField != null ? sortOptions.sortField.name().toLowerCase() : null,
                sortOptions.sortOrder != null ? sortOptions.sortOrder.name().toLowerCase() : null);
    }

    /**
     * Get player season stats with full pagination and sort options.
     */
    public PlayerSeasonStatsResponse getPlayerSeasonStats(
            String careerId,
            Integer season,
            String teamId,
            String playerId,
            int limit,
            int offset,
            String sortBy,
            String order) {

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

        log.info("[V24-STATS-QUERY] careerId={}, teamId={}, details.size={}", careerId, teamId, details.size());

        // [V24D6M11-TRACE] Log actual seasonNumbers per detail before filter
        for (int i = 0; i < details.size(); i++) {
            V24DetailedMatchData d = details.get(i);
            log.info("[V24D6M11-TRACE] detail[{}] matchId={}, seasonNumber={}, round={}, homeTeamId={}, awayTeamId={}",
                    i, d.matchId(), d.seasonNumber(), d.round(), d.homeTeamId(), d.awayTeamId());
        }

        // DEBUG: log distinct seasonNumbers
        var seasonNumbers = details.stream()
                .map(V24DetailedMatchData::seasonNumber)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        log.info("[V24-STATS-SEASON] season param={}, distinct seasonNumbers in details={}", season, seasonNumbers);

        List<V24DetailedMatchData> seasonDetails = details.stream()
                .filter(d -> d.seasonNumber() != null && d.seasonNumber().equals(season))
                .collect(Collectors.toList());

        log.info("[V24-STATS-SEASON] seasonDetails.size={} after filter with season={}", seasonDetails.size(), season);

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

        // Resolve sort field from string
        PlayerSeasonStatsAggregator.SortField sortField =
                resolveSortField(sortBy, PlayerSeasonStatsAggregator.SortField.GOALS);
        PlayerSeasonStatsAggregator.SortOrder sortOrder =
                resolveSortOrder(order, PlayerSeasonStatsAggregator.SortOrder.DESC);

        PlayerSeasonStatsAggregator.FilterOptions filterOpts =
                new PlayerSeasonStatsAggregator.FilterOptions(teamId, playerId);
        PlayerSeasonStatsAggregator.SortOptions sortOpts =
                new PlayerSeasonStatsAggregator.SortOptions(sortField, sortOrder, limit, offset);

        AggregationResult result = aggregator.aggregateWithMetadata(
                seasonDetails, careerId, season, filterOpts, sortOpts);

        log.info("[V24-STATS-AGG] playerStatsSize={}, totalMatchesProcessed={}, matchIds={}, teamId={}",
                result.playerStats().size(), result.totalMatchesProcessed(), result.matchIds(), teamId);

        List<PlayerSeasonStatsWarning> warnings = buildWarnings(result);
        PlayerSeasonStatsMetadata metadata = buildMetadata(
                result, careerId, season, limit, offset, teamId, playerId, sortOpts);

        return new PlayerSeasonStatsResponse(
                careerId, season,
                result.playerStats(),
                result.totalGoals(),
                result.totalAssists(),
                result.totalAppearances(),
                result.averageRating(),
                false,
                null,
                metadata,
                warnings
        );
    }

    private PlayerSeasonStatsAggregator.SortField resolveSortField(String sortBy,
            PlayerSeasonStatsAggregator.SortField defaultField) {
        if (sortBy == null || sortBy.isBlank()) return defaultField;
        PlayerSeasonStatsSortField field = PlayerSeasonStatsSortField.fromString(sortBy);
        if (field == null) return defaultField;
        // Map PlayerSeasonStatsSortField to internal SortField
        return switch (field) {
            case GOALS -> PlayerSeasonStatsAggregator.SortField.GOALS;
            case ASSISTS -> PlayerSeasonStatsAggregator.SortField.ASSISTS;
            case AVERAGE_RATING -> PlayerSeasonStatsAggregator.SortField.RATING;
            case APPEARANCES -> PlayerSeasonStatsAggregator.SortField.APPEARANCES;
            case STARTS -> PlayerSeasonStatsAggregator.SortField.STARTS;
            case SHOTS -> PlayerSeasonStatsAggregator.SortField.SHOTS;
            case KEY_PASSES -> PlayerSeasonStatsAggregator.SortField.KEY_PASSES;
            case YELLOW_CARDS -> PlayerSeasonStatsAggregator.SortField.YELLOW_CARDS;
            case RED_CARDS -> PlayerSeasonStatsAggregator.SortField.RED_CARDS;
            case INJURIES -> PlayerSeasonStatsAggregator.SortField.INJURIES;
            case FOULS -> PlayerSeasonStatsAggregator.SortField.FOULS;
            case PLAYER_NAME -> PlayerSeasonStatsAggregator.SortField.PLAYER_NAME;
        };
    }

    private PlayerSeasonStatsAggregator.SortOrder resolveSortOrder(String order,
            PlayerSeasonStatsAggregator.SortOrder defaultOrder) {
        if (order == null || order.isBlank()) return defaultOrder;
        String o = order.toLowerCase();
        if (o.equals("asc")) return PlayerSeasonStatsAggregator.SortOrder.ASC;
        if (o.equals("desc")) return PlayerSeasonStatsAggregator.SortOrder.DESC;
        return defaultOrder;
    }

    private List<PlayerSeasonStatsWarning> buildWarnings(AggregationResult result) {
        List<PlayerSeasonStatsWarning> warnings = new ArrayList<>();

        if (!result.playerStats().isEmpty()) {
            warnings.add(new PlayerSeasonStatsWarning(
                    PlayerSeasonStatsWarningCode.APPROXIMATE_APPEARANCES,
                    "appearances is approximate — substitute appearances may be undercounted",
                    "appearances"));

            warnings.add(new PlayerSeasonStatsWarning(
                    PlayerSeasonStatsWarningCode.APPROXIMATE_MATCHES_MISSED,
                    "matchesMissedInjured/matchesMissedSuspended are approximated",
                    "matchesMissedInjuredApprox"));
        }

        if (result.matchIds().isEmpty()) {
            warnings.add(new PlayerSeasonStatsWarning(
                    PlayerSeasonStatsWarningCode.NO_DETAIL_DATA,
                    "No V24 detail data found for this career/season",
                    null));
        }

        return warnings;
    }

    private PlayerSeasonStatsMetadata buildMetadata(AggregationResult result,
            String careerId, Integer season, int limit, int offset,
            String teamId, String playerId, PlayerSeasonStatsAggregator.SortOptions sortOpts) {

        int totalPlayers = result.totalPlayers();
        List<String> matchIds = result.matchIds();
        int lastRound = result.lastUpdatedRound();

        PlayerSeasonStatsDataCompleteness completeness;
        if (matchIds.isEmpty()) {
            completeness = PlayerSeasonStatsDataCompleteness.EMPTY;
        } else {
            completeness = PlayerSeasonStatsDataCompleteness.COMPLETE;
        }

        String versionHash = computeVersionHash(matchIds);

        return PlayerSeasonStatsMetadata.builder()
                .limit(limit)
                .offset(offset)
                .hasMore(offset + result.playerStats().size() < totalPlayers)
                .totalPlayers(totalPlayers)
                .returnedPlayers(result.playerStats().size())
                .totalMatchesProcessed(result.totalMatchesProcessed())
                .lastUpdatedRound(lastRound)
                .dataSource("V24_DETAIL")
                .dataCompleteness(completeness)
                .generatedAt(Instant.now())
                .versionHash(versionHash)
                .build();
    }

    private String computeVersionHash(List<String> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) return "empty";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = String.join("|", matchIds);
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(matchIds.hashCode());
        }
    }

    /**
     * Check whether the stats API is enabled.
     */
    public boolean isApiEnabled() {
        return apiEnabled;
    }
}