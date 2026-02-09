package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.TeamId;
import java.time.Instant;
import java.util.Objects;

/**
 * Standing - Entity que representa la tabla de posiciones de una temporada/torneo.
 * Vive en Redis (cache) como parte de CareerSave o independiente.
 *
 * NO tiene persistencia en PostgreSQL - se reconstruye desde Redis.
 */
public class Standing {

    public enum Status {
        ACTIVE,
        COMPLETED
    }

    private String id;              // "seasonKey:teamId" composite key
    private String seasonKey;       // Puede ser seasonId (int) o tournamentId (UUID) como String
    private String teamId;
    private int played;
    private int wins;
    private int draws;
    private int losses;
    private int goalsFor;
    private int goalsAgainst;
    private int goalDifference;
    private int points;
    private Status status;
    private Instant lastUpdated;

    private Standing() {
        this.played = 0;
        this.wins = 0;
        this.draws = 0;
        this.losses = 0;
        this.goalsFor = 0;
        this.goalsAgainst = 0;
        this.goalDifference = 0;
        this.points = 0;
        this.status = Status.ACTIVE;
        this.lastUpdated = Instant.now();
    }

    public Standing(String seasonKey, TeamId teamId) {
        this();
        this.id = generateId(seasonKey, teamId.getValue());
        this.seasonKey = seasonKey;
        this.teamId = teamId.getValue().toString();
    }

    public Standing(String seasonKey, java.util.UUID teamId) {
        this();
        this.id = generateId(seasonKey, teamId);
        this.seasonKey = seasonKey;
        this.teamId = teamId.toString();
    }

    private static String generateId(String seasonKey, java.util.UUID teamId) {
        return seasonKey + ":" + teamId.toString();
    }

    /**
     * Actualiza el standing con el resultado de un partido.
     */
    public void updateResult(int homeGoals, int awayGoals, boolean isHomeTeam) {
        this.played++;
        this.goalsFor += isHomeTeam ? homeGoals : awayGoals;
        this.goalsAgainst += isHomeTeam ? awayGoals : homeGoals;
        this.goalDifference = this.goalsFor - this.goalsAgainst;

        if ((isHomeTeam && homeGoals > awayGoals) || (!isHomeTeam && awayGoals > homeGoals)) {
            this.wins++;
            this.points += 3;
        } else if (homeGoals == awayGoals) {
            this.draws++;
            this.points += 1;
        } else {
            this.losses++;
        }

        this.lastUpdated = Instant.now();
    }

    // ========== Getters ==========

    public String getId() { return id; }
    public String getSeasonKey() { return seasonKey; }
    public String getTeamId() { return teamId; }
    public int getPlayed() { return played; }
    public int getWins() { return wins; }
    public int getDraws() { return draws; }
    public int getLosses() { return losses; }
    public int getGoalsFor() { return goalsFor; }
    public int getGoalsAgainst() { return goalsAgainst; }
    public int getGoalDifference() { return goalDifference; }
    public int getPoints() { return points; }
    public Status getStatus() { return status; }
    public Instant getLastUpdated() { return lastUpdated; }

    // ========== Setters ==========

    public void setStatus(Status status) { this.status = status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Standing standing = (Standing) o;
        return Objects.equals(id, standing.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Standing{seasonKey=%s, teamId=%s, pts=%d, p=%d, w=%d, d=%d, l=%d, gf=%d, ga=%d}",
                seasonKey, teamId, points, played, wins, draws, losses, goalsFor, goalsAgainst);
    }
}
