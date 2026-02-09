package com.footballmanager.domain.model.entity.career;

/**
 * Representa el conteo de títulos de un equipo.
 */
public class TitleCount {

    private String teamId;
    private String teamName;
    private String coachName;
    private int titles;

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getCoachName() { return coachName; }
    public void setCoachName(String coachName) { this.coachName = coachName; }
    public int getTitles() { return titles; }
    public void setTitles(int titles) { this.titles = titles; }
}
