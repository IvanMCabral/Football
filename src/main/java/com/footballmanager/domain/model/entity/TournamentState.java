package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.footballmanager.domain.model.valueobject.MatchEvent;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.MatchStatus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TournamentState - Estado completo del torneo en una carrera.
 * Se guarda en Redis como parte de CareerSave.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TournamentState {

    private Integer currentRound = 1;
    private Integer totalRounds = 0;
    private Boolean finished = false;
    private String championTeamId;
    private CareerPhase careerPhase = CareerPhase.PRE_MATCH;

    private List<MatchFixture> fixtures = new ArrayList<>();
    private Map<String, TeamStandings> standings = new HashMap<>();
    // Final standings stored per division after tournament finishes
    private Map<String, List<TeamStandings>> divisionFinalStandings = new HashMap<>();

    public TournamentState() {}

    // ========== Phase Management ==========

    public void startMatchDay() {
        validatePhase(CareerPhase.PRE_MATCH);
        careerPhase = CareerPhase.IN_MATCH;
    }

    public void finishMatchDay() {
        validatePhase(CareerPhase.IN_MATCH);
        careerPhase = CareerPhase.POST_MATCH;
    }

    public void enterWaitingUserPhase() {
        validatePhase(CareerPhase.POST_MATCH);
        careerPhase = CareerPhase.WAITING_USER;
    }

    public boolean advanceToNextRound() {
        validatePhase(CareerPhase.WAITING_USER);
        if (currentRound >= totalRounds) return false;
        currentRound++;
        careerPhase = CareerPhase.PRE_MATCH;
        return true;
    }

    public TournamentResult finishTournament(int season, String teamId, String teamName, String coachName) {
        validatePhase(CareerPhase.WAITING_USER);
        this.championTeamId = teamId;
        this.finished = true;
        this.careerPhase = CareerPhase.FINISHED;
        return new TournamentResult(season, teamId, teamName, coachName);
    }

    public void resetForNewSeason() {
        this.currentRound = 1;
        this.finished = false;
        this.championTeamId = null;
        this.careerPhase = CareerPhase.PRE_MATCH;
        this.fixtures = new ArrayList<>();
        this.standings = new HashMap<>();
        // Limpiar clasificaciones finales de la temporada anterior
        this.divisionFinalStandings.clear();
    }

    private void validatePhase(CareerPhase expected) {
        if (careerPhase != expected) {
            throw new IllegalStateException("Phase " + careerPhase + ". Expected: " + expected);
        }
    }

    // ========== Phase Queries ==========

    public boolean canAdvanceToNextRound() { return careerPhase == CareerPhase.WAITING_USER && !finished; }
    public boolean canFinishTournament() { return careerPhase == CareerPhase.WAITING_USER && currentRound >= totalRounds && !finished; }
    public boolean canStartMatchDay() { return careerPhase == CareerPhase.PRE_MATCH && !finished; }

    // ========== Fixture Management ==========

    public List<MatchFixture> getFixturesForRound(int round) {
        return fixtures.stream().filter(f -> f.getRound() == round).collect(Collectors.toList());
    }

    public MatchFixture getFixture(String matchId) {
        return fixtures.stream().filter(f -> f.getMatchId().equals(matchId)).findFirst().orElse(null);
    }

    public void recordMatchResult(String matchId, MatchFixture.MatchResultData result) {
        MatchFixture fixture = getFixture(matchId);
        if (fixture == null) throw new IllegalArgumentException("Fixture not found: " + matchId);
        fixture.complete(result);
        updateStandingsWithResult(fixture);
    }

    public void processMatchResult(String matchId, int homeGoals, int awayGoals, List<MatchEvent> events) {
        MatchFixture fixture = getFixture(matchId);
        if (fixture == null) throw new IllegalArgumentException("Fixture not found: " + matchId);
        if (fixture.getStatus() != MatchStatus.PENDING) throw new IllegalStateException("Already processed: " + matchId);

        MatchFixture.MatchResultData result = new MatchFixture.MatchResultData(homeGoals, awayGoals, 0, 0, 0, 0);
        fixture.complete(result);
        fixtures = fixtures.stream().map(f -> f.getMatchId().equals(matchId) ? fixture : f).collect(Collectors.toList());
        updateStandingsWithResult(fixture);
    }

    // ========== Standings Management ==========

    public void initializeStandings(List<SessionTeam> teams) {
        teams.forEach(t -> standings.put(t.getSessionTeamId(), new TeamStandings(t.getSessionTeamId(), t.getName())));
    }

    public void updateStandingsWithResult(MatchFixture fixture) {
        if (fixture.getResult() == null) return;

        TeamStandings home = standings.get(fixture.getHomeTeamId());
        TeamStandings away = standings.get(fixture.getAwayTeamId());
        if (home == null || away == null) return;

        MatchFixture.MatchResultData r = fixture.getResult();
        updateTeamStats(home, r.isHomeWin(), r.isDraw(), r.getHomeGoals(), r.getAwayGoals());
        // FIX: Use isAwayWin() instead of !isHomeWin() to correctly handle draws
        updateTeamStats(away, r.isAwayWin(), r.isDraw(), r.getAwayGoals(), r.getHomeGoals());
    }

    private void updateTeamStats(TeamStandings team, boolean win, boolean draw, int goalsFor, int goalsAgainst) {
        team.setPlayed(team.getPlayed() + 1);
        team.setGoalsFor(team.getGoalsFor() + goalsFor);
        team.setGoalsAgainst(team.getGoalsAgainst() + goalsAgainst);
        team.setGoalDifference(team.getGoalsFor() - team.getGoalsAgainst());

        if (win) { team.setWon(team.getWon() + 1); team.setPoints(team.getPoints() + 3); }
        else if (draw) { team.setDrawn(team.getDrawn() + 1); team.setPoints(team.getPoints() + 1); }
        else { team.setLost(team.getLost() + 1); }
    }

    public List<TeamStandings> getSortedStandings() {
        return standings.values().stream()
            .sorted(Comparator.comparing(TeamStandings::getPoints).reversed()
                .thenComparing(TeamStandings::getGoalDifference, Comparator.reverseOrder())
                .thenComparing(TeamStandings::getGoalsFor, Comparator.reverseOrder()))
            .collect(Collectors.toList());
    }

    public String determineChampion() {
        return standings.values().stream()
            .max(Comparator.comparing(TeamStandings::getPoints)
                .thenComparing(TeamStandings::getGoalDifference)
                .thenComparing(TeamStandings::getGoalsFor))
            .map(TeamStandings::getTeamId).orElse(null);
    }

    public void advanceRound() {
        if (currentRound >= totalRounds) {
            finished = true;
            championTeamId = determineChampion();
        } else {
            currentRound++;
        }
    }

    // ========== Getters/Setters ==========

    public Integer getCurrentRound() { return currentRound; }
    public void setCurrentRound(Integer r) { this.currentRound = r; }
    public Integer getTotalRounds() { return totalRounds; }
    public void setTotalRounds(Integer r) { this.totalRounds = r; }
    public Boolean getFinished() { return finished; }
    public void setFinished(Boolean f) { this.finished = f; }
    public String getChampionTeamId() { return championTeamId; }
    public void setChampionTeamId(String id) { this.championTeamId = id; }
    public List<MatchFixture> getFixtures() { return fixtures; }
    public void setFixtures(List<MatchFixture> f) {
        this.fixtures.clear();
        if (f != null) {
            this.fixtures.addAll(f);
        }
    }
    public Map<String, TeamStandings> getStandings() { return standings; }
    public void setStandings(Map<String, TeamStandings> s) {
        this.standings.clear();
        if (s != null) {
            this.standings.putAll(s);
        }
    }
    public CareerPhase getCareerPhase() { return careerPhase; }
    public void setCareerPhase(CareerPhase p) { this.careerPhase = p; }

    // ========== Division Final Standings ==========

    /**
     * Store final standings for a specific division.
     * Used for calculating promotions/relegations.
     */
    public void setDivisionFinalStandings(String divisionId, List<TeamStandings> finalStandings) {
        this.divisionFinalStandings.put(divisionId, new ArrayList<>(finalStandings));
    }

    /**
     * Get final standings for a specific division.
     */
    public List<TeamStandings> getDivisionFinalStandings(String divisionId) {
        return divisionFinalStandings.getOrDefault(divisionId, new ArrayList<>());
    }

    public Map<String, List<TeamStandings>> getDivisionFinalStandingsMap() { return divisionFinalStandings; }
}
