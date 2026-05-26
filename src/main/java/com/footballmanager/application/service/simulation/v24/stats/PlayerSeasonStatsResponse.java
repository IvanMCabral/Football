package com.footballmanager.application.service.simulation.v24.stats;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * V24D6M3: Response DTO for player season stats query.
 *
 * <p>Contains a list of per-player season stat records plus optional
 * computed summaries and an incomplete flag for partial data.
 */
public final class PlayerSeasonStatsResponse {

    private final String careerId;
    private final Integer season;
    private final List<PlayerSeasonStatsDto> playerStats;
    private final Integer totalGoals;
    private final Integer totalAssists;
    private final Integer totalAppearances;
    private final Double averageRating;
    private final boolean incomplete;
    private final String message;

    public PlayerSeasonStatsResponse(
            String careerId,
            Integer season,
            List<PlayerSeasonStatsDto> playerStats,
            Integer totalGoals,
            Integer totalAssists,
            Integer totalAppearances,
            Double averageRating,
            boolean incomplete,
            String message) {
        this.careerId = Objects.requireNonNull(careerId, "careerId must not be null");
        this.season = season;
        this.playerStats = (playerStats != null) ? Collections.unmodifiableList(playerStats) : Collections.emptyList();
        this.totalGoals = totalGoals;
        this.totalAssists = totalAssists;
        this.totalAppearances = totalAppearances;
        this.averageRating = averageRating;
        this.incomplete = incomplete;
        this.message = message;
    }

    public String careerId() { return careerId; }
    public Integer season() { return season; }
    public List<PlayerSeasonStatsDto> playerStats() { return playerStats; }
    public Integer totalGoals() { return totalGoals; }
    public Integer totalAssists() { return totalAssists; }
    public Integer totalAppearances() { return totalAppearances; }
    public Double averageRating() { return averageRating; }
    public boolean incomplete() { return incomplete; }
    public String message() { return message; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerSeasonStatsResponse that)) return false;
        return Objects.equals(careerId, that.careerId)
                && Objects.equals(season, that.season)
                && Objects.equals(playerStats, that.playerStats)
                && Objects.equals(totalGoals, that.totalGoals)
                && Objects.equals(totalAssists, that.totalAssists)
                && Objects.equals(totalAppearances, that.totalAppearances)
                && Double.compare(that.averageRating, averageRating) == 0
                && incomplete == that.incomplete
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(careerId, season, playerStats, totalGoals, totalAssists,
                totalAppearances, averageRating, incomplete, message);
    }

    @Override
    public String toString() {
        return "PlayerSeasonStatsResponse{careerId=%s, season=%s, players=%d, incomplete=%s}"
                .formatted(careerId, season, playerStats.size(), incomplete);
    }
}
