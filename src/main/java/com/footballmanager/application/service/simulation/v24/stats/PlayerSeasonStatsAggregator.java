package com.footballmanager.application.service.simulation.v24.stats;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * V24D6M3: Pure aggregator for player season stats.
 *
 * <p>Computes per-player season aggregates from a list of V24DetailedMatchData.
 * No side effects, no external I/O, no Random — fully deterministic.
 *
 * <p>MVP scope (per M2 audit):
 * <ul>
 *   <li>Only starting XI players appear in playerRatings (V24PlayerRatingsAssembler limitation)</li>
 *   <li>minutesPlayed is NOT available — not included</li>
 *   <li>shotsOnTarget is NOT available — not included</li>
 *   <li>form/energy are NOT available — not included</li>
 *   <li>matchesMissedInjuredApprox = injuries × {@value #DEFAULT_INJURY_MISSED_MATCHES_ESTIMATE}</li>
 *   <li>matchesMissedSuspendedApprox = redCards + floor(yellowCards / 5)</li>
 * </ul>
 */
public final class PlayerSeasonStatsAggregator {

    public static final int DEFAULT_INJURY_MISSED_MATCHES_ESTIMATE = 2;
    public static final int YELLOWS_PER_SUSPENSION = 5;

    /**
     * Aggregate stats — convenience overload, no filters, default sort.
     */
    public PlayerSeasonStatsResponse aggregate(
            List<V24DetailedMatchData> details,
            String careerId,
            Integer season) {
        return aggregate(details, careerId, season, FilterOptions.NONE, SortOptions.DEFAULT);
    }

    /**
     * Aggregate stats with optional filters and sort options.
     *
     * @param filter optional filters (null = no filter)
     * @param sort optional sort (null = DEFAULT)
     */
    public PlayerSeasonStatsResponse aggregate(
            List<V24DetailedMatchData> details,
            String careerId,
            Integer season,
            FilterOptions filter,
            SortOptions sort) {

        if (details == null || details.isEmpty()) {
            return emptyResponse(careerId, season);
        }

        FilterOptions f = filter != null ? filter : FilterOptions.NONE;
        SortOptions s = sort != null ? sort : SortOptions.DEFAULT;

        // Deduplicate by matchId
        Map<String, V24DetailedMatchData> deduped = new HashMap<>();
        for (V24DetailedMatchData detail : details) {
            if (detail != null && detail.matchId() != null) {
                deduped.putIfAbsent(detail.matchId(), detail);
            }
        }

        // Accumulate per (teamId, playerId)
        Map<String, PlayerAccumulator> accumulators = new HashMap<>();

        for (V24DetailedMatchData detail : deduped.values()) {
            if (detail.playerRatings() == null) continue;
            for (V24PlayerMatchRatingDto rating : detail.playerRatings()) {
                if (rating == null || rating.playerId() == null) continue;

                String tId = rating.teamId();
                String pId = rating.playerId();

                if (f.teamId != null && !f.teamId.equals(tId)) continue;
                if (f.playerId != null && !f.playerId.equals(pId)) continue;

                String key = tId + "|" + pId;
                PlayerAccumulator acc = accumulators.computeIfAbsent(key,
                        k -> new PlayerAccumulator(careerId, season, tId, pId,
                                rating.playerName(), rating.position()));
                acc.addMatch(detail.round(), rating);
            }
        }

        List<PlayerSeasonStatsDto> dtos = new ArrayList<>();
        for (PlayerAccumulator acc : accumulators.values()) {
            dtos.add(acc.toDto());
        }

        // Sort
        dtos.sort(getComparator(s.sortField, s.sortOrder));

        // Limit
        if (s.limit > 0 && dtos.size() > s.limit) {
            dtos = dtos.subList(0, s.limit);
        }

        // Totals
        int totalGoals = dtos.stream().mapToInt(PlayerSeasonStatsDto::goals).sum();
        int totalAssists = dtos.stream().mapToInt(PlayerSeasonStatsDto::assists).sum();
        int totalAppearances = dtos.stream().mapToInt(PlayerSeasonStatsDto::appearances).sum();
        double avgRating = dtos.isEmpty() ? 0.0
                : dtos.stream().mapToDouble(PlayerSeasonStatsDto::averageRating).average().orElse(0.0);

        return new PlayerSeasonStatsResponse(
                careerId, season, dtos,
                totalGoals, totalAssists, totalAppearances,
                Math.round(avgRating * 100.0) / 100.0,
                false,
                null
        );
    }

    private PlayerSeasonStatsResponse emptyResponse(String careerId, Integer season) {
        return new PlayerSeasonStatsResponse(careerId, season, List.of(), 0, 0, 0, 0.0, false, null);
    }

    private Comparator<PlayerSeasonStatsDto> getComparator(SortField field, SortOrder order) {
        Comparator<PlayerSeasonStatsDto> cmp;
        switch (field) {
            case ASSISTS:      cmp = Comparator.comparingInt(PlayerSeasonStatsDto::assists); break;
            case RATING:      cmp = Comparator.comparingDouble(PlayerSeasonStatsDto::averageRating); break;
            case APPEARANCES: cmp = Comparator.comparingInt(PlayerSeasonStatsDto::appearances); break;
            case PLAYER_NAME:  cmp = Comparator.comparing(PlayerSeasonStatsDto::playerName); break;
            case GOALS:
            default:          cmp = Comparator.comparingInt(PlayerSeasonStatsDto::goals); break;
        }
        if (order == SortOrder.DESC) cmp = cmp.reversed();
        return cmp.thenComparing(PlayerSeasonStatsDto::playerName);
    }

    private static final class PlayerAccumulator {
        private final String careerId;
        private final Integer season;
        private final String teamId;
        private final String playerId;
        private final String playerName;
        private final String position;
        private int goals, assists, keyPasses, shots;
        private int yellowCards, redCards, injuries, fouls;
        private int appearances, starts;
        private double ratingSum, bestRating, worstRating;
        private int lastUpdatedRound;

        PlayerAccumulator(String careerId, Integer season, String teamId,
                          String playerId, String playerName, String position) {
            this.careerId = careerId;
            this.season = season;
            this.teamId = teamId;
            this.playerId = playerId;
            this.playerName = playerName;
            this.position = position;
            this.bestRating = Double.MIN_VALUE;
            this.worstRating = Double.MAX_VALUE;
        }

        void addMatch(int round, V24PlayerMatchRatingDto r) {
            appearances++;
            if (!r.substitutedIn()) starts++;
            goals += r.goals();
            assists += r.assists();
            keyPasses += r.keyPasses();
            shots += r.shots();
            yellowCards += r.yellowCards();
            redCards += r.redCards();
            injuries += r.injuries();
            fouls += r.fouls();
            double rating = r.rating();
            if (rating > bestRating) bestRating = rating;
            if (rating < worstRating) worstRating = rating;
            ratingSum += rating;
            if (round > lastUpdatedRound) lastUpdatedRound = round;
        }

        PlayerSeasonStatsDto toDto() {
            double avgRating = appearances > 0 ? ratingSum / appearances : 0.0;
            if (bestRating == Double.MIN_VALUE) bestRating = 0.0;
            if (worstRating == Double.MAX_VALUE) worstRating = 0.0;
            int missedInjuredApprox = injuries * DEFAULT_INJURY_MISSED_MATCHES_ESTIMATE;
            int missedSuspendedApprox = redCards + (yellowCards / YELLOWS_PER_SUSPENSION);
            return new PlayerSeasonStatsDto(
                    careerId, season, teamId, playerId, playerName, position,
                    appearances, starts,
                    goals, assists, keyPasses, shots,
                    yellowCards, redCards, injuries, fouls,
                    missedInjuredApprox, missedSuspendedApprox,
                    Math.round(avgRating * 100.0) / 100.0,
                    Math.round(bestRating * 100.0) / 100.0,
                    Math.round(worstRating * 100.0) / 100.0,
                    lastUpdatedRound
            );
        }
    }

    public enum SortField { GOALS, ASSISTS, RATING, APPEARANCES, PLAYER_NAME }
    public enum SortOrder { ASC, DESC }

    public static final class FilterOptions {
        public static final FilterOptions NONE = new FilterOptions(null, null);
        public final String teamId;
        public final String playerId;
        public FilterOptions(String teamId, String playerId) {
            this.teamId = teamId;
            this.playerId = playerId;
        }
    }

    public static final class SortOptions {
        public static final SortOptions DEFAULT = new SortOptions(SortField.GOALS, SortOrder.DESC, 0);
        public final SortField sortField;
        public final SortOrder sortOrder;
        public final int limit;
        public SortOptions(SortField sortField, SortOrder sortOrder, int limit) {
            this.sortField = sortField != null ? sortField : SortField.GOALS;
            this.sortOrder = sortOrder != null ? sortOrder : SortOrder.DESC;
            this.limit = limit;
        }
    }
}
