package com.footballmanager.application.service.simulation.v24.stats;

import java.util.List;

/**
 * V24D6M7: Result of the aggregation step, carrying both the paginated
 * player stats AND the metadata needed to build the response.
 */
public record AggregationResult(
        List<PlayerSeasonStatsDto> playerStats,
        int totalGoals,
        int totalAssists,
        int totalAppearances,
        double averageRating,
        List<String> matchIds,
        int totalMatchesProcessed,
        int lastUpdatedRound,
        int totalPlayers
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private List<PlayerSeasonStatsDto> playerStats = List.of();
        private int totalGoals = 0;
        private int totalAssists = 0;
        private int totalAppearances = 0;
        private double averageRating = 0.0;
        private List<String> matchIds = List.of();
        private int totalMatchesProcessed = 0;
        private int lastUpdatedRound = 0;
        private int totalPlayers = 0;

        public Builder playerStats(List<PlayerSeasonStatsDto> stats) { this.playerStats = stats; return this; }
        public Builder totalGoals(int v) { this.totalGoals = v; return this; }
        public Builder totalAssists(int v) { this.totalAssists = v; return this; }
        public Builder totalAppearances(int v) { this.totalAppearances = v; return this; }
        public Builder averageRating(double v) { this.averageRating = v; return this; }
        public Builder matchIds(List<String> ids) { this.matchIds = ids; return this; }
        public Builder totalMatchesProcessed(int v) { this.totalMatchesProcessed = v; return this; }
        public Builder lastUpdatedRound(int v) { this.lastUpdatedRound = v; return this; }
        public Builder totalPlayers(int v) { this.totalPlayers = v; return this; }

        public AggregationResult build() {
            return new AggregationResult(playerStats, totalGoals, totalAssists,
                    totalAppearances, averageRating, matchIds, totalMatchesProcessed, lastUpdatedRound, totalPlayers);
        }
    }
}