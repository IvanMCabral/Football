package com.footballmanager.domain.model;

import java.time.Instant;
import java.util.*;

public class League {
    private final LeagueId id;
    private final String name;
    private final String country;
    private final Set<TeamId> teamIds;
    private final Instant createdAt;
    private Instant updatedAt;

    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_TEAMS = 20;

    private League(LeagueId id, String name, String country, Set<TeamId> teamIds,
                  Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "LeagueId cannot be null");
        this.country = Objects.requireNonNull(country, "Country cannot be null");
        
        validateName(name);
        
        this.name = name;
        this.teamIds = teamIds != null ? new HashSet<>(teamIds) : new HashSet<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static League create(LeagueId id, String name, String country) {
        return new League(id, name, country, new HashSet<>(), Instant.now(), Instant.now());
    }

    public static League reconstruct(LeagueId id, String name, String country, Set<TeamId> teamIds,
                                    Instant createdAt, Instant updatedAt) {
        return new League(id, name, country, teamIds, createdAt, updatedAt);
    }

    private void validateName(String name) {
        if (name == null || name.trim().length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                String.format("League name must be between %d and %d characters", 
                            MIN_NAME_LENGTH, MAX_NAME_LENGTH)
            );
        }
    }

    public void addTeam(TeamId teamId) {
        Objects.requireNonNull(teamId, "TeamId cannot be null");
        if (teamIds.size() >= MAX_TEAMS) {
            throw new IllegalStateException("League is full (maximum " + MAX_TEAMS + " teams)");
        }
        teamIds.add(teamId);
        this.updatedAt = Instant.now();
    }

    public void removeTeam(TeamId teamId) {
        Objects.requireNonNull(teamId, "TeamId cannot be null");
        teamIds.remove(teamId);
        this.updatedAt = Instant.now();
    }

    public int getTeamCount() {
        return teamIds.size();
    }

    // Getters
    public LeagueId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public Set<TeamId> getTeamIds() {
        return Collections.unmodifiableSet(teamIds);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        League league = (League) o;
        return Objects.equals(id, league.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("League{id=%s, name='%s', country='%s', teams=%d}",
                id, name, country, teamIds.size());
    }
}
