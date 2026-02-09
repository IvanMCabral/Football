package com.footballmanager.domain.model.aggregate;

import com.footballmanager.domain.model.valueobject.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Team - Entidad de PostgreSQL para equipos base.
 * El gameplay usa WorldTeam (IA) y SessionTeam (usuario).
 */
public class Team {
    private final TeamId id;
    private final UserId managerId;
    private final String name;
    private final String country;
    private BigDecimal budget;
    private Formation formation;
    private final Set<PlayerId> squadPlayerIds;
    private final Instant createdAt;
    private Instant updatedAt;

    private static final int MAX_SQUAD_SIZE = 30;
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 100;

    private Team(TeamId id, UserId managerId, String name, String country, 
                 BigDecimal budget, Formation formation, Set<PlayerId> squadPlayerIds,
                 Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "TeamId cannot be null");
        this.managerId = Objects.requireNonNull(managerId, "ManagerId cannot be null");
        this.country = Objects.requireNonNull(country, "Country cannot be null");
        this.formation = Objects.requireNonNull(formation, "Formation cannot be null");
        
        validateName(name);
        validateBudget(budget);
        
        this.name = name;
        this.budget = budget;
        this.squadPlayerIds = squadPlayerIds != null ? new HashSet<>(squadPlayerIds) : new HashSet<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Team create(TeamId id, UserId managerId, String name, String country,
                             BigDecimal budget, Formation formation) {
        return new Team(id, managerId, name, country, budget, formation, 
                       new HashSet<>(), Instant.now(), Instant.now());
    }

    public static Team reconstruct(TeamId id, UserId managerId, String name, String country,
                                  BigDecimal budget, Formation formation, Set<PlayerId> squadPlayerIds,
                                  Instant createdAt, Instant updatedAt) {
        return new Team(id, managerId, name, country, budget, formation, 
                       squadPlayerIds, createdAt, updatedAt);
    }

    private void validateName(String name) {
        if (name == null || name.trim().length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Team name must be between %d and %d characters", MIN_NAME_LENGTH, MAX_NAME_LENGTH)
            );
        }
    }

    private void validateBudget(BigDecimal budget) {
        if (budget == null || budget.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Budget cannot be negative");
        }
    }

    public void addPlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "PlayerId cannot be null");
        if (squadPlayerIds.size() >= MAX_SQUAD_SIZE) {
            throw new IllegalStateException("Squad is full (maximum " + MAX_SQUAD_SIZE + " players)");
        }
        squadPlayerIds.add(playerId);
        this.updatedAt = Instant.now();
    }

    public void removePlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "PlayerId cannot be null");
        squadPlayerIds.remove(playerId);
        this.updatedAt = Instant.now();
    }

    public void updateBudget(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        BigDecimal newBudget = this.budget.add(amount);
        if (newBudget.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Budget cannot go below zero");
        }
        this.budget = newBudget;
        this.updatedAt = Instant.now();
    }

    public void updateFormation(Formation formation) {
        this.formation = Objects.requireNonNull(formation, "Formation cannot be null");
        this.updatedAt = Instant.now();
    }

    public boolean isBankrupt() {
        return budget.compareTo(BigDecimal.ZERO) <= 0;
    }

    public int getSquadSize() {
        return squadPlayerIds.size();
    }

    // Getters
    public TeamId getId() {
        return id;
    }

    public UserId getManagerId() {
        return managerId;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public Formation getFormation() {
        return formation;
    }

    public Set<PlayerId> getSquadPlayerIds() {
        return Collections.unmodifiableSet(squadPlayerIds);
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
        Team team = (Team) o;
        return Objects.equals(id, team.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Team{id=%s, name='%s', country='%s', budget=%s, squadSize=%d, formation=%s}",
                id, name, country, budget, squadPlayerIds.size(), formation);
    }
}
