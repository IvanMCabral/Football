package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Division/Category within a career.
 * Contains teams and basic division info.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Division {

    private String divisionId;
    private String name;
    private int divisionNumber;
    private List<String> teamIds;
    private Instant createdAt;

    public Division() {
        this.divisionId = UUID.randomUUID().toString();
        this.teamIds = new ArrayList<>();
        this.createdAt = Instant.now();
    }

    public Division(String name, int divisionNumber) {
        this();
        this.name = name;
        this.divisionNumber = divisionNumber;
    }

    // ========== Team management ==========

    public void addTeam(String sessionTeamId) {
        if (!teamIds.contains(sessionTeamId)) {
            teamIds.add(sessionTeamId);
        }
    }

    public void removeTeam(String sessionTeamId) {
        teamIds.remove(sessionTeamId);
    }

    public boolean containsTeam(String sessionTeamId) {
        return teamIds.contains(sessionTeamId);
    }

    public int getTeamCount() {
        return teamIds.size();
    }

    // ========== Round calculations ==========

    public int getTotalRounds() {
        int count = teamIds.size();
        return count % 2 == 0 ? 2 * (count - 1) : 2 * count;
    }

    public boolean hasByeInRound(int round) {
        return teamIds.size() % 2 != 0 && round > getTotalRounds() / 2;
    }

    // ========== Display name ==========

    @JsonIgnore
    public String getDisplayName() {
        return switch (divisionNumber) {
            case 1 -> "Primera División";
            case 2 -> "Segunda División";
            case 3 -> "Tercera División";
            default -> name + " (" + divisionNumber + "ª)";
        };
    }

    // ========== Getters/Setters ==========

    public String getDivisionId() { return divisionId; }
    public void setDivisionId(String id) { this.divisionId = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDivisionNumber() { return divisionNumber; }
    public void setDivisionNumber(int n) { this.divisionNumber = n; }

    public List<String> getTeamIds() { return teamIds; }
    public void setTeamIds(List<String> ids) {
        this.teamIds.clear();
        if (ids != null) {
            this.teamIds.addAll(ids);
        }
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant t) { this.createdAt = t; }
}
