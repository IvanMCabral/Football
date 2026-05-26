package com.footballmanager.application.service.simulation.v24.stats;

import java.util.Objects;

/**
 * V24D6M3: Per-player season aggregate stats.
 *
 * <p>Computed from V24DetailedMatchData.playerRatings across all matches
 * in a career/season. This DTO is immutable once constructed.
 *
 * <p>MVP fields only — deferred fields (minutesPlayed, shotsOnTarget, form,
 * energy) are not included per M2 audit findings.
 *
 * <p>Approximate fields are marked with "Approx" suffix and carry known
 * limitations documented in the design doc (V24D6M_PLAYER_SEASON_STATS_DESIGN.md).
 */
public final class PlayerSeasonStatsDto {

    // Identity
    private final String careerId;
    private final Integer season;
    private final String teamId;
    private final String playerId;
    private final String playerName;
    private final String position;

    // Appearance
    private final int appearances;
    private final int starts;

    // Attack
    private final int goals;
    private final int assists;
    private final int keyPasses;
    private final int shots;

    // Discipline
    private final int yellowCards;
    private final int redCards;

    // Health
    private final int injuries;
    private final int fouls;

    // Approximate — see design doc M2 findings
    private final int matchesMissedInjuredApprox;
    private final int matchesMissedSuspendedApprox;

    // Performance ratings
    private final double averageRating;
    private final double bestRating;
    private final double worstRating;

    // Metadata
    private final int lastUpdatedRound;

    public PlayerSeasonStatsDto(
            String careerId,
            Integer season,
            String teamId,
            String playerId,
            String playerName,
            String position,
            int appearances,
            int starts,
            int goals,
            int assists,
            int keyPasses,
            int shots,
            int yellowCards,
            int redCards,
            int injuries,
            int fouls,
            int matchesMissedInjuredApprox,
            int matchesMissedSuspendedApprox,
            double averageRating,
            double bestRating,
            double worstRating,
            int lastUpdatedRound) {
        this.careerId = Objects.requireNonNull(careerId, "careerId must not be null");
        this.season = season;
        this.teamId = teamId;
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Unknown";
        this.position = position;
        this.appearances = appearances;
        this.starts = starts;
        this.goals = goals;
        this.assists = assists;
        this.keyPasses = keyPasses;
        this.shots = shots;
        this.yellowCards = yellowCards;
        this.redCards = redCards;
        this.injuries = injuries;
        this.fouls = fouls;
        this.matchesMissedInjuredApprox = matchesMissedInjuredApprox;
        this.matchesMissedSuspendedApprox = matchesMissedSuspendedApprox;
        this.averageRating = averageRating;
        this.bestRating = bestRating;
        this.worstRating = worstRating;
        this.lastUpdatedRound = lastUpdatedRound;
    }

    // Identity getters
    public String careerId() { return careerId; }
    public Integer season() { return season; }
    public String teamId() { return teamId; }
    public String playerId() { return playerId; }
    public String playerName() { return playerName; }
    public String position() { return position; }

    // Appearance getters
    public int appearances() { return appearances; }
    public int starts() { return starts; }

    // Attack getters
    public int goals() { return goals; }
    public int assists() { return assists; }
    public int keyPasses() { return keyPasses; }
    public int shots() { return shots; }

    // Discipline getters
    public int yellowCards() { return yellowCards; }
    public int redCards() { return redCards; }

    // Health getters
    public int injuries() { return injuries; }
    public int fouls() { return fouls; }

    // Approximate getters (documented limitations — see design doc)
    /**
     * Approximate matches missed due to injury.
     * Derived from injury event count × DEFAULT_INJURY_MISSED_MATCHES_ESTIMATE.
     * Does not reflect actual round-by-round injury state.
     */
    public int matchesMissedInjuredApprox() { return matchesMissedInjuredApprox; }

    /**
     * Approximate matches missed due to suspension.
     * Derived from: redCards + floor(yellowCards / 5).
     * May double-count if yellowCards is already season-cumulative in source data.
     */
    public int matchesMissedSuspendedApprox() { return matchesMissedSuspendedApprox; }

    // Rating getters
    public double averageRating() { return averageRating; }
    public double bestRating() { return bestRating; }
    public double worstRating() { return worstRating; }

    // Metadata getters
    public int lastUpdatedRound() { return lastUpdatedRound; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerSeasonStatsDto that)) return false;
        return Objects.equals(careerId, that.careerId)
                && Objects.equals(season, that.season)
                && Objects.equals(teamId, that.teamId)
                && Objects.equals(playerId, that.playerId)
                && Objects.equals(playerName, that.playerName)
                && Objects.equals(position, that.position)
                && appearances == that.appearances
                && starts == that.starts
                && goals == that.goals
                && assists == that.assists
                && keyPasses == that.keyPasses
                && shots == that.shots
                && yellowCards == that.yellowCards
                && redCards == that.redCards
                && injuries == that.injuries
                && fouls == that.fouls
                && matchesMissedInjuredApprox == that.matchesMissedInjuredApprox
                && matchesMissedSuspendedApprox == that.matchesMissedSuspendedApprox
                && Double.compare(that.averageRating, averageRating) == 0
                && Double.compare(that.bestRating, bestRating) == 0
                && Double.compare(that.worstRating, worstRating) == 0
                && lastUpdatedRound == that.lastUpdatedRound;
    }

    @Override
    public int hashCode() {
        return Objects.hash(careerId, season, teamId, playerId, playerName, position,
                appearances, starts, goals, assists, keyPasses, shots,
                yellowCards, redCards, injuries, fouls,
                matchesMissedInjuredApprox, matchesMissedSuspendedApprox,
                averageRating, bestRating, worstRating, lastUpdatedRound);
    }

    @Override
    public String toString() {
        return "PlayerSeasonStatsDto{player='%s', team=%s, G=%d, A=%d, R=%.2f}"
                .formatted(playerName, teamId, goals, assists, averageRating);
    }
}
