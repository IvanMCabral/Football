package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.metadata.WorldIdentityDomain;
import com.footballmanager.domain.model.metadata.WorldIdentityReference;

import java.time.Instant;

public class TournamentResult {
    private int season;
    @WorldIdentityReference(domain = WorldIdentityDomain.OTHER_ID, nullable = true)
    private String divisionId;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT, nullable = true)
    private String divisionName;
    @WorldIdentityReference(domain = WorldIdentityDomain.SESSION_TEAM)
    private String championTeamId;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT)
    private String championTeamName;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT)
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
