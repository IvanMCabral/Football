package com.footballmanager.domain.model.entity.career;

import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentResult;
import com.footballmanager.domain.model.entity.TournamentState;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * Fachada para la gestión de temporadas.
 * Coordina: SeasonInfo, DivisionManager, PromotionCalculator, PromotionExecutor, TitleManager.
 */
@Slf4j
public class CareerSeasonManager {

    private SeasonInfo seasonInfo = new SeasonInfo();
    private final DivisionManager divisionManager = new DivisionManager();
    private final PromotionCalculator promotionCalculator = new PromotionCalculator();
    private final PromotionExecutor promotionExecutor = new PromotionExecutor();
    private final TitleManager titleManager = new TitleManager();

    public CareerSeasonManager() {
    }

    // ========== Season Info ==========

    public void startNewSeason() {
        this.seasonInfo = seasonInfo.withNextSeason();
        log.info("[SEASON] Started season {}", seasonInfo.currentSeason());
    }

    public int getCurrentSeason() {
        return seasonInfo.currentSeason();
    }

    public void setCurrentSeason(int season) {
        this.seasonInfo = new SeasonInfo(season, seasonInfo.promotionsCalculated());
    }

    // ========== Division Management ==========

    public void assignTeamsToDivisions(List<SessionTeam> teams, Comparator<SessionTeam> teamComparator, int teamsPerDivision) {
        divisionManager.assignTeamsToDivisions(teams, teamComparator, teamsPerDivision);
    }

    public Division findDivisionByTeamId(String teamId) {
        return divisionManager.findDivisionByTeamId(teamId);
    }

    public List<Division> getDivisions() {
        return divisionManager.getDivisions();
    }

    public void setDivisions(List<Division> divisions) {
        divisionManager.setDivisions(divisions);
    }

    public List<String> getFreeTeams() {
        return divisionManager.getFreeTeams();
    }

    public void setFreeTeams(List<String> freeTeams) {
        divisionManager.setFreeTeams(freeTeams);
    }

    public int getTotalDivisions() {
        return divisionManager.getTotalDivisions();
    }

    // ========== Promotions ==========

    /**
     * Calcula ascensos y descensos (solo una vez por temporada).
     */
    public List<Promotion> calculatePromotionsAndRelegations(TournamentState tournamentState) {
        if (seasonInfo.promotionsCalculated()) {
            log.info("[PROMOTIONS] Already calculated for this season, skipping");
            return promotionCalculator.getPromotions();
        }

        List<Division> sortedDivisions = divisionManager.getSortedDivisions();
        promotionCalculator.calculate(sortedDivisions, tournamentState);
        this.seasonInfo = seasonInfo.withPromotionsCalculated();

        return promotionCalculator.getPromotions();
    }

    /**
     * Ejecuta las promociones calculadas.
     */
    public void executePromotionsAndRelegations() {
        List<Promotion> promotions = promotionCalculator.getPromotions();
        promotionExecutor.execute(promotions, divisionManager);
    }

    /**
     * Obtiene las promociones ejecutadas en la última temporada.
     */
    public List<Promotion> getLastExecutedPromotions() {
        return promotionExecutor.getLastExecutedPromotions();
    }

    /**
     * Obtiene la lista temporal de promociones calculadas.
     */
    public List<Promotion> getPromotions() {
        return promotionCalculator.getPromotions();
    }

    // ========== Palmares / Titles ==========

    public List<TournamentResult> getPalmares() {
        return titleManager.getTournamentResults();
    }

    public void setPalmares(List<TournamentResult> palmares) {
        titleManager.setTournamentResults(palmares);
    }

    public void addTournamentResult(TournamentResult result) {
        titleManager.addTournamentResult(result);
    }

    public List<TitleCount> getTopTeams() {
        return titleManager.getTopTeams();
    }

    public void setTopTeams(List<TitleCount> topTeams) {
        titleManager.setTopTeams(topTeams);
    }

    public void updateTopTeams(TournamentResult result) {
        titleManager.updateTopTeams(result);
    }
}
