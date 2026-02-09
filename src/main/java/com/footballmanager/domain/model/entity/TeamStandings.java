package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TeamStandings - Posiciones de un equipo en la tabla.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamStandings {

    private String teamId;
    private String teamName;
    private Integer played = 0;
    private Integer won = 0;
    private Integer drawn = 0;
    private Integer lost = 0;
    private Integer goalsFor = 0;
    private Integer goalsAgainst = 0;
    private Integer goalDifference = 0;
    private Integer points = 0;

    public TeamStandings() {}

    public TeamStandings(String teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
    }

    // ========== Getters ==========

    public String getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public Integer getPlayed() { return played; }
    public Integer getWon() { return won; }
    public Integer getDrawn() { return drawn; }
    public Integer getLost() { return lost; }
    public Integer getGoalsFor() { return goalsFor; }
    public Integer getGoalsAgainst() { return goalsAgainst; }
    public Integer getGoalDifference() { return goalDifference; }
    public Integer getPoints() { return points; }

    // ========== Setters ==========

    public void setTeamId(String id) { this.teamId = id; }
    public void setTeamName(String name) { this.teamName = name; }
    public void setPlayed(Integer p) { this.played = p; }
    public void setWon(Integer w) { this.won = w; }
    public void setDrawn(Integer d) { this.drawn = d; }
    public void setLost(Integer l) { this.lost = l; }
    public void setGoalsFor(Integer g) { this.goalsFor = g; }
    public void setGoalsAgainst(Integer g) { this.goalsAgainst = g; }
    public void setGoalDifference(Integer d) { this.goalDifference = d; }
    public void setPoints(Integer p) { this.points = p; }
}
