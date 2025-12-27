package com.footballmanager.domain.model;

import java.time.Instant;
import java.util.Objects;

public class Match {
    private final MatchId id;
    private final TeamId homeTeamId;
    private final TeamId awayTeamId;
    private final Instant scheduledAt;
    private MatchStatus status;
    private MatchResult result;
    private final Instant createdAt;
    private Instant simulatedAt;

    public enum MatchStatus {
        SCHEDULED, SIMULATED, CANCELLED
    }

    private Match(MatchId id, TeamId homeTeamId, TeamId awayTeamId, Instant scheduledAt,
                 MatchStatus status, MatchResult result, Instant createdAt, Instant simulatedAt) {
        this.id = Objects.requireNonNull(id, "MatchId cannot be null");
        this.homeTeamId = Objects.requireNonNull(homeTeamId, "Home TeamId cannot be null");
        this.awayTeamId = Objects.requireNonNull(awayTeamId, "Away TeamId cannot be null");
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "Scheduled time cannot be null");
        
        validateTeams(homeTeamId, awayTeamId);
        
        this.status = status != null ? status : MatchStatus.SCHEDULED;
        this.result = result;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.simulatedAt = simulatedAt;
    }

    public static Match create(MatchId id, TeamId homeTeamId, TeamId awayTeamId, Instant scheduledAt) {
        return new Match(id, homeTeamId, awayTeamId, scheduledAt, 
                        MatchStatus.SCHEDULED, null, Instant.now(), null);
    }

    public static Match reconstruct(MatchId id, TeamId homeTeamId, TeamId awayTeamId, Instant scheduledAt,
                                   MatchStatus status, MatchResult result, Instant createdAt, Instant simulatedAt) {
        return new Match(id, homeTeamId, awayTeamId, scheduledAt, 
                        status, result, createdAt, simulatedAt);
    }

    private void validateTeams(TeamId homeTeamId, TeamId awayTeamId) {
        if (homeTeamId.equals(awayTeamId)) {
            throw new IllegalArgumentException("Home team and away team cannot be the same");
        }
    }

    public void simulate(MatchResult result) {
        if (this.status == MatchStatus.SIMULATED) {
            throw new IllegalStateException("Match has already been simulated");
        }
        if (this.status == MatchStatus.CANCELLED) {
            throw new IllegalStateException("Cannot simulate a cancelled match");
        }
        
        this.result = Objects.requireNonNull(result, "Match result cannot be null");
        this.status = MatchStatus.SIMULATED;
        this.simulatedAt = Instant.now();
    }

    public void cancel() {
        if (this.status == MatchStatus.SIMULATED) {
            throw new IllegalStateException("Cannot cancel a simulated match");
        }
        this.status = MatchStatus.CANCELLED;
    }

    public boolean isSimulated() {
        return status == MatchStatus.SIMULATED;
    }

    public boolean isCancelled() {
        return status == MatchStatus.CANCELLED;
    }

    public boolean isScheduled() {
        return status == MatchStatus.SCHEDULED;
    }

    // Getters
    public MatchId getId() {
        return id;
    }

    public TeamId getHomeTeamId() {
        return homeTeamId;
    }

    public TeamId getAwayTeamId() {
        return awayTeamId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public MatchResult getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSimulatedAt() {
        return simulatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Match match = (Match) o;
        return Objects.equals(id, match.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Match{id=%s, home=%s vs away=%s, status=%s, scheduledAt=%s}",
                id, homeTeamId, awayTeamId, status, scheduledAt);
    }
}
