package com.footballmanager.domain.model.entity;

import java.time.Instant;

public class TournamentResult {
    private int season;
    private String divisionId;
    private String divisionName;
    private String championTeamId;
    private String championTeamName;
    private String championCoachName;
    private Instant createdAt;

    public TournamentResult() {}
    // Constructor legacy (4 parámetros)
    public TournamentResult(int season, String championTeamId, String championTeamName, String championCoachName) {
        this(season, null, null, championTeamId, championTeamName, championCoachName);
    }
    // Constructor nuevo con división
    public TournamentResult(int season, String divisionId, String divisionName,
                           String championTeamId, String championTeamName, String championCoachName) {
        this.season = season;
        this.divisionId = divisionId;
        this.divisionName = divisionName;
        this.championTeamId = championTeamId;
        this.championTeamName = championTeamName;
        this.championCoachName = championCoachName;
        this.createdAt = Instant.now();
    }

    public int getSeason() { return season; }
    public void setSeason(int season) { this.season = season; }
    public String getDivisionId() { return divisionId; }
    public void setDivisionId(String divisionId) { this.divisionId = divisionId; }
    public String getDivisionName() { return divisionName; }
    public void setDivisionName(String divisionName) { this.divisionName = divisionName; }
    public String getChampionTeamId() { return championTeamId; }
    public void setChampionTeamId(String championTeamId) { this.championTeamId = championTeamId; }
    public String getChampionTeamName() { return championTeamName; }
    public void setChampionTeamName(String championTeamName) { this.championTeamName = championTeamName; }
    public String getChampionCoachName() { return championCoachName; }
    public void setChampionCoachName(String championCoachName) { this.championCoachName = championCoachName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
