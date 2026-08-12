package com.footballmanager.domain.model.entity.career;

import com.footballmanager.domain.model.metadata.WorldIdentityDomain;
import com.footballmanager.domain.model.metadata.WorldIdentityReference;

/**
 * Representa el conteo de títulos de un equipo.
 */
public class TitleCount {

    @WorldIdentityReference(domain = WorldIdentityDomain.SESSION_TEAM)
    private String teamId;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT)
    private String teamName;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT)
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
