package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Match aggregate representing a football match.
 *
 * Design principles:
 * - Immutable towards outside (no public setters)
 * - State transitions through domain methods with intent
 * - Validation at each state change
 * - JSON serialization compatible for persistence
 */
public class Match {

    // === Core Identity (Immutable) ===
    private final MatchId id;
    private final TeamId homeTeamId;
    private final TeamId awayTeamId;
    private final Instant scheduledAt;
    private final Integer round;
    private final GameId gameId;  // For persistence compatibility

    // === State (Mutable only through domain methods) ===
    private MatchStatus status;
    private MatchResult result;
    private Instant simulatedAt;

    // === Statistics (Set during simulation) ===
    private Integer homeGoals;
    private Integer awayGoals;
    private Integer homeShots;
    private Integer awayShots;
    private Integer homePossession;
    private Integer awayPossession;
    private String summary;

    // === Players (Set before simulation) ===
    private List<PlayerId> participatingPlayerIds;

    // === Metadata (Immutable) ===
    private final Instant createdAt;

    // Private constructor - use factory methods
    private Match(MatchId id, TeamId homeTeamId, TeamId awayTeamId,
                  Instant scheduledAt, Integer round, GameId gameId,
                  MatchStatus status, MatchResult result, Instant simulatedAt,
                  Integer homeGoals, Integer awayGoals,
                  Integer homeShots, Integer awayShots,
                  Integer homePossession, Integer awayPossession,
                  String summary, List<PlayerId> participatingPlayerIds,
                  Instant createdAt) {
        this.id = Objects.requireNonNull(id, "MatchId cannot be null");
        this.homeTeamId = Objects.requireNonNull(homeTeamId, "Home TeamId cannot be null");
        this.awayTeamId = Objects.requireNonNull(awayTeamId, "Away TeamId cannot be null");
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "Scheduled time cannot be null");
        this.round = round;
        this.gameId = gameId;
        this.status = status != null ? status : MatchStatus.SCHEDULED;
        this.result = result;
        this.simulatedAt = simulatedAt;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.homeShots = homeShots;
        this.awayShots = awayShots;
        this.homePossession = homePossession;
        this.awayPossession = awayPossession;
        this.summary = summary;
        this.participatingPlayerIds = participatingPlayerIds;
        this.createdAt = createdAt != null ? createdAt : Instant.now();

        validateTeamsNotSame();
    }

    // === Factory Methods ===

    /**
     * Creates a new scheduled match.
     */
    public static Match schedule(MatchId id, TeamId homeTeamId, TeamId awayTeamId,
                                  Instant scheduledAt, int round) {
        return new Match(
            id, homeTeamId, awayTeamId, scheduledAt, round, null,
            MatchStatus.SCHEDULED, null, null,
            null, null, null, null, null, null, null, null,
            Instant.now()
        );
    }

    /**
     * Reconstructs a match from persistence.
     * Package-private for repository/adapter access.
     */
    public static Match reconstruct(MatchId id, TeamId homeTeamId, TeamId awayTeamId,
                              Instant scheduledAt, MatchStatus status,
                              MatchResult result, Instant createdAt,
                              Instant simulatedAt, GameId gameId, Integer round) {
        return new Match(
            id, homeTeamId, awayTeamId, scheduledAt, round, gameId,
            status, result, simulatedAt,
            result != null ? result.getHomeGoals() : null,
            result != null ? result.getAwayGoals() : null,
            result != null ? result.getHomeShots() : null,
            result != null ? result.getAwayShots() : null,
            result != null ? result.getHomePossession() : null,
            result != null ? result.getAwayPossession() : null,
            result != null ? result.getSummary() : null,
            null, // participatingPlayerIds - not persisted in current schema
            createdAt
        );
    }

    // === Domain Methods (State Transitions) ===

    /**
     * Sets participating players before simulation.
     * Validates that match is in SCHEDULED state.
     */
    public void assignPlayers(List<PlayerId> playerIds) {
        if (this.status != MatchStatus.SCHEDULED) {
            throw new IllegalStateException(
                "Cannot assign players. Match status is " + status +
                ". Expected: SCHEDULED"
            );
        }
        this.participatingPlayerIds = List.copyOf(playerIds);
    }

    /**
     * Simulates the match with the given result.
     * Validates preconditions and transitions to SIMULATED state.
     *
     * @param matchResult The match result with goals and statistics
     * @return The simulated result
     * @throws IllegalStateException if match cannot be simulated
     */
    public MatchResult simulate(MatchResult matchResult) {
        validateCanSimulate();

        this.result = Objects.requireNonNull(matchResult, "Match result cannot be null");
        this.status = MatchStatus.SIMULATED;
        this.simulatedAt = Instant.now();

        // Extract statistics from result
        this.homeGoals = matchResult.getHomeGoals();
        this.awayGoals = matchResult.getAwayGoals();
        this.homeShots = matchResult.getHomeShots();
        this.awayShots = matchResult.getAwayShots();
        this.homePossession = matchResult.getHomePossession();
        this.awayPossession = matchResult.getAwayPossession();
        this.summary = matchResult.getSummary();

        return this.result;
    }

    /**
     * Convenience method: creates result from goals and simulates.
     *
     * @param homeGoals Goals scored by home team
     * @param awayGoals Goals scored by away team
     * @return The simulated match result
     */
    public MatchResult simulateWithGoals(int homeGoals, int awayGoals) {
        // Use default values for possession (50-50) and shots (proportional to goals)
        MatchResult result = MatchResult.of(
            homeGoals, awayGoals,
            50, 50,  // 50% possession each
            homeGoals * 3, awayGoals * 3,  // 3 shots per goal estimate
            List.of(),
            null
        );
        return simulate(result);
    }

    /**
     * Cancels the match.
     * Only scheduled matches can be cancelled.
     *
     * @throws IllegalStateException if match is already simulated
     */
    public void cancel() {
        if (this.status == MatchStatus.SIMULATED) {
            throw new IllegalStateException("Cannot cancel a simulated match");
        }
        if (this.status == MatchStatus.CANCELLED) {
            throw new IllegalStateException("Match is already cancelled");
        }
        this.status = MatchStatus.CANCELLED;
    }

    // === Validation Private Methods ===

    private void validateTeamsNotSame() {
        if (homeTeamId.equals(awayTeamId)) {
            throw new IllegalArgumentException("Home team and away team cannot be the same");
        }
    }

    private void validateCanSimulate() {
        if (this.status == MatchStatus.SIMULATED) {
            throw new IllegalStateException("Match has already been simulated");
        }
        if (this.status == MatchStatus.CANCELLED) {
            throw new IllegalStateException("Cannot simulate a cancelled match");
        }
    }

    // === Query Methods (Read-only) ===

    public MatchId getId() { return id; }
    public TeamId getHomeTeamId() { return homeTeamId; }
    public TeamId getAwayTeamId() { return awayTeamId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Integer getRound() { return round; }
    public GameId getGameId() { return gameId; }  // For persistence
    public MatchStatus getStatus() { return status; }
    public MatchResult getResult() { return result; }
    public Instant getSimulatedAt() { return simulatedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public List<PlayerId> getParticipatingPlayerIds() {
        return participatingPlayerIds != null
            ? List.copyOf(participatingPlayerIds)
            : null;
    }

    // Statistics getters (nullable if not simulated)
    public Integer getHomeGoals() { return homeGoals; }
    public Integer getAwayGoals() { return awayGoals; }
    public Integer getHomeShots() { return homeShots; }
    public Integer getAwayShots() { return awayShots; }
    public Integer getHomePossession() { return homePossession; }
    public Integer getAwayPossession() { return awayPossession; }
    public String getSummary() { return summary; }

    // === State Query Methods ===

    public boolean isSimulated() { return status == MatchStatus.SIMULATED; }
    public boolean isCancelled() { return status == MatchStatus.CANCELLED; }
    public boolean isScheduled() { return status == MatchStatus.SCHEDULED; }
    public boolean isPending() { return status == MatchStatus.PENDING; }

    public boolean hasEnded() {
        return status == MatchStatus.SIMULATED || status == MatchStatus.CANCELLED;
    }

    // === Equality (Identity-based) ===

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
