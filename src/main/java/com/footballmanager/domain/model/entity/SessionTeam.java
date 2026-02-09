package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * SessionTeam - Equipo que existe SOLO en Redis durante una carrera.
 * NO se persiste en base de datos real.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionTeam {

    // Identity
    private String sessionTeamId;
    private UUID baseTeamId;
    private String worldTeamId;

    // Core info
    private String name;
    private String country;
    private BigDecimal budget;
    private String formation;
    @JsonAlias("coachName")
    private String managerName;

    // Dynamic state
    private Integer morale;
    private Integer reputation;

    // Metadata
    private SessionTeamOrigin origin;
    private Instant createdAt;
    private Instant lastUpdated;

    public enum SessionTeamOrigin { CLONED, CUSTOM, RANDOM }

    // ========== Factory Methods ==========

    // Aliases for backward compatibility
    public static SessionTeam cloneFromRealTeam(UUID realTeamId, String worldTeamId,
            String name, String country, BigDecimal budget, String formation) {
        return fromRealTeam(realTeamId, worldTeamId, name, country, budget, formation, null);
    }

    public static SessionTeam cloneFromRealTeam(UUID realTeamId, String worldTeamId,
            String name, String country, BigDecimal budget, String formation, String coachName) {
        return fromRealTeam(realTeamId, worldTeamId, name, country, budget, formation, coachName);
    }

    public static SessionTeam createRandom(String worldTeamId, String name, String country, BigDecimal budget) {
        return random(worldTeamId, name, country, budget, null);
    }

    public static SessionTeam createRandom(String worldTeamId, String name, String country, BigDecimal budget, String coachName) {
        return random(worldTeamId, name, country, budget, coachName);
    }

    public static SessionTeam fromRealTeam(UUID realTeamId, String worldTeamId,
            String name, String country, BigDecimal budget, String formation, String coachName) {
        SessionTeam t = new SessionTeam();
        t.sessionTeamId = UUID.randomUUID().toString();
        t.baseTeamId = realTeamId;
        t.worldTeamId = worldTeamId;
        t.name = name;
        t.country = country;
        t.budget = budget;
        t.formation = formation != null ? formation : "4-3-3";
        t.managerName = coachName;
        t.initDefaults();
        t.origin = SessionTeamOrigin.CLONED;
        return t;
    }

    public static SessionTeam custom(String worldTeamId, String name, String country,
            BigDecimal budget, String formation) {
        SessionTeam t = new SessionTeam();
        t.sessionTeamId = UUID.randomUUID().toString();
        t.baseTeamId = null;
        t.worldTeamId = worldTeamId;
        t.name = name;
        t.country = country;
        t.budget = budget;
        t.formation = formation != null ? formation : "4-3-3";
        t.initDefaults();
        t.origin = SessionTeamOrigin.CUSTOM;
        return t;
    }

    public static SessionTeam random(String worldTeamId, String name, String country,
            BigDecimal budget, String coachName) {
        SessionTeam t = new SessionTeam();
        t.sessionTeamId = UUID.randomUUID().toString();
        t.baseTeamId = null;
        t.worldTeamId = worldTeamId;
        t.name = name;
        t.country = country;
        t.budget = budget;
        t.formation = randomFormation();
        t.managerName = coachName;
        t.morale = 40 + (int)(Math.random() * 30);
        t.reputation = 30 + (int)(Math.random() * 40);
        t.origin = SessionTeamOrigin.RANDOM;
        t.createdAt = Instant.now();
        t.lastUpdated = Instant.now();
        return t;
    }

    private void initDefaults() {
        this.morale = 50;
        this.reputation = 50;
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    private static String randomFormation() {
        String[] formations = {"4-3-3", "4-4-2", "3-5-2", "4-2-3-1", "5-3-2", "4-1-4-1"};
        return formations[(int)(Math.random() * formations.length)];
    }

    // ========== Business Methods ==========

    public void updateBudget(BigDecimal amount) {
        this.budget = this.budget.add(amount);
        this.lastUpdated = Instant.now();
    }

    public void updateMorale(int delta) {
        this.morale = Math.max(0, Math.min(100, this.morale + delta));
        this.lastUpdated = Instant.now();
    }

    public void updateFormation(String newFormation) {
        this.formation = newFormation;
        this.lastUpdated = Instant.now();
    }

    // ========== Getters ==========

    public String getSessionTeamId() { return sessionTeamId; }
    public UUID getBaseTeamId() { return baseTeamId; }
    public String getWorldTeamId() { return worldTeamId; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public BigDecimal getBudget() { return budget; }
    public String getFormation() { return formation; }
    public String getManagerName() { return managerName; }
    public String getCoachName() { return managerName; }
    public Integer getMorale() { return morale; }
    public Integer getReputation() { return reputation; }
    public SessionTeamOrigin getOrigin() { return origin; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUpdated() { return lastUpdated; }

    // ========== Setters ==========

    public void setSessionTeamId(String id) { this.sessionTeamId = id; }
    public void setBaseTeamId(UUID id) { this.baseTeamId = id; }
    public void setWorldTeamId(String id) { this.worldTeamId = id; }
    public void setName(String name) { this.name = name; }
    public void setCountry(String country) { this.country = country; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }
    public void setFormation(String formation) { this.formation = formation; }
    public void setManagerName(String name) { this.managerName = name; }
    public void setMorale(Integer morale) { this.morale = morale; }
    public void setReputation(Integer reputation) { this.reputation = reputation; }
    public void setOrigin(SessionTeamOrigin origin) { this.origin = origin; }
    public void setCreatedAt(Instant time) { this.createdAt = time; }
    public void setLastUpdated(Instant time) { this.lastUpdated = time; }

    @Override
    public String toString() {
        return String.format("SessionTeam{id='%s', name='%s', country='%s', formation='%s'}",
                sessionTeamId, name, country, formation);
    }
}
