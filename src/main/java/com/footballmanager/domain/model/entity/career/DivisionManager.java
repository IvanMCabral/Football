package com.footballmanager.domain.model.entity.career;

import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.SessionTeam;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gestiona las divisiones de una carrera.
 * Asignación de equipos, búsqueda y estructura de divisiones.
 */
@Slf4j
public class DivisionManager {

    private List<Division> divisions = new ArrayList<>();
    private List<String> freeTeams = new ArrayList<>();

    public DivisionManager() {
    }

    public void assignTeamsToDivisions(List<SessionTeam> teams, Comparator<SessionTeam> teamComparator, int teamsPerDivision) {
        List<SessionTeam> sortedTeams = new ArrayList<>(teams);
        sortedTeams.sort(teamComparator);

        divisions.clear();
        freeTeams.clear();

        int totalTeams = sortedTeams.size();
        int divisionNum = 1;
        int i = 0;

        while (i < totalTeams) {
            int remainingTeams = totalTeams - i;
            int teamsInThisDivision = Math.min(teamsPerDivision, remainingTeams);

            if (teamsInThisDivision >= 2) {
                Division div = new Division("Division " + divisionNum, divisionNum);
                for (int j = 0; j < teamsInThisDivision; j++) {
                    div.addTeam(sortedTeams.get(i + j).getSessionTeamId());
                }
                divisions.add(div);
                divisionNum++;
                i += teamsInThisDivision;
            } else {
                for (int j = i; j < totalTeams; j++) {
                    freeTeams.add(sortedTeams.get(j).getSessionTeamId());
                }
                break;
            }
        }

        log.info("[DIVISIONS] Assigned {} teams to {} divisions, {} free teams",
            totalTeams, divisions.size(), freeTeams.size());
    }

    public Division findDivisionByTeamId(String teamId) {
        if (teamId == null) return null;
        return divisions.stream()
                .filter(d -> d.containsTeam(teamId))
                .findFirst()
                .orElse(null);
    }

    public Division findDivisionById(String divisionId) {
        return divisions.stream()
                .filter(d -> d.getDivisionId().equals(divisionId))
                .findFirst()
                .orElse(null);
    }

    public void moveTeam(String teamId, Division fromDivision, Division toDivision) {
        if (fromDivision != null && toDivision != null) {
            fromDivision.removeTeam(teamId);
            toDivision.addTeam(teamId);
        }
    }

    public List<Division> getDivisions() {
        return divisions;
    }

    public void setDivisions(List<Division> divisions) {
        this.divisions.clear();
        if (divisions != null) {
            this.divisions.addAll(divisions);
        }
    }

    public List<String> getFreeTeams() {
        return freeTeams;
    }

    public void setFreeTeams(List<String> freeTeams) {
        this.freeTeams.clear();
        if (freeTeams != null) {
            this.freeTeams.addAll(freeTeams);
        }
    }

    public int getTotalDivisions() {
        return divisions.size();
    }

    public List<Division> getSortedDivisions() {
        return divisions.stream()
                .sorted(Comparator.comparing(Division::getDivisionNumber))
                .toList();
    }
}
