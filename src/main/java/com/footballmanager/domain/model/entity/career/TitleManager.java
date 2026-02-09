package com.footballmanager.domain.model.entity.career;

import com.footballmanager.domain.model.entity.TournamentResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Gestiona el historial de títulos y equipos top.
 */
public class TitleManager {

    private final List<TournamentResult> tournamentResults = new ArrayList<>();
    private final List<TitleCount> internalTopTeams = new ArrayList<>();

    public TitleManager() {
    }

    public List<TournamentResult> getTournamentResults() {
        return tournamentResults;
    }

    public void setTournamentResults(List<TournamentResult> results) {
        tournamentResults.clear();
        if (results != null) {
            tournamentResults.addAll(results);
        }
    }

    /**
     * Agrega un resultado de torneo con deduplicación.
     */
    public void addTournamentResult(TournamentResult result) {
        boolean exists = tournamentResults.stream()
            .anyMatch(p -> p.getSeason() == result.getSeason() &&
                          Objects.equals(p.getDivisionId(), result.getDivisionId()));

        if (!exists) {
            tournamentResults.add(result);
        }
    }

    public List<TitleCount> getTopTeams() {
        return internalTopTeams;
    }

    public void setTopTeams(List<TitleCount> topTeams) {
        internalTopTeams.clear();
        if (topTeams != null) {
            internalTopTeams.addAll(topTeams);
        }
    }

    /**
     * Actualiza el conteo de títulos para el equipo campeón.
     */
    public void updateTopTeams(TournamentResult result) {
        String teamId = result.getChampionTeamId();
        String teamName = result.getChampionTeamName();
        String coachName = result.getChampionCoachName();

        TitleCount tc = internalTopTeams.stream()
            .filter(t -> t.getTeamId().equals(teamId))
            .findFirst()
            .orElse(null);

        if (tc == null) {
            tc = new TitleCount();
            tc.setTeamId(teamId);
            tc.setTeamName(teamName);
            tc.setCoachName(coachName);
            tc.setTitles(1);
            internalTopTeams.add(tc);
        } else {
            tc.setTitles(tc.getTitles() + 1);
        }
    }
}
