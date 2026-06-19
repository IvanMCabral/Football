package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.footballmanager.domain.model.entity.career.*;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerSeasonManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.application.service.query.TeamOVRQueryService;

import java.time.Instant;
import java.util.*;

/**
 * CareerSave - Save game completo de una carrera.
 * Se persiste en Redis con clave: career:{userId}
 *
 * Delegated responsibilities:
 * - CareerData: metadata y configuración
 * - CareerTeamManager: equipos y squads
 * - CareerPlayerManager: jugadores
 * - CareerSeasonManager: temporadas y divisiones
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CareerSave {

    private CareerData data = new CareerData();
    private CareerTeamManager teamManager = new CareerTeamManager();
    private CareerPlayerManager playerManager = new CareerPlayerManager();
    private CareerSeasonManager seasonManager = new CareerSeasonManager();
    private Map<String, List<String>> teamStarting11 = new HashMap<>();
    /**
     * MVP1-lineup-cancha-1: subdivisionId por jugador (mapa interno:
     * teamId → { subdivisionId → playerId }). Paralelo a
     * {@link #teamStarting11} — no lo reemplaza. Si está vacío o ausente
     * para un team, se infiere on-the-fly del role del jugador (backward
     * compat con lineups viejos).
     */
    private Map<String, Map<String, String>> teamStarting11Subdivision = new HashMap<>();
    private TournamentState tournamentState = new TournamentState();

    // Setters requeridos para deserialización JSON
    public void setData(CareerData data) { this.data = data; }
    public void setTeamManager(CareerTeamManager teamManager) { this.teamManager = teamManager; }
    public void setPlayerManager(CareerPlayerManager playerManager) { this.playerManager = playerManager; }
    public void setSeasonManager(CareerSeasonManager seasonManager) {
        this.seasonManager = seasonManager;
    }
    public void setTeamStarting11(Map<String, List<String>> starting11) {
        this.teamStarting11.clear();
        this.teamStarting11.putAll(starting11);
    }
    public void setTeamStarting11Subdivision(Map<String, Map<String, String>> slots) {
        this.teamStarting11Subdivision = (slots == null) ? new HashMap<>() : new HashMap<>(slots);
    }
    public void setTournamentState(TournamentState state) { this.tournamentState = state; }

    // ========== Core accessors (for services) ==========

    public CareerData getData() { return data; }
    public CareerTeamManager getTeamManager() { return teamManager; }
    public CareerPlayerManager getPlayerManager() { return playerManager; }
    public CareerSeasonManager getSeasonManager() { return seasonManager; }
    public Map<String, List<String>> getTeamStarting11() { return teamStarting11; }
    public Map<String, Map<String, String>> getTeamStarting11Subdivision() {
        if (teamStarting11Subdivision == null) {
            teamStarting11Subdivision = new HashMap<>();
        }
        return teamStarting11Subdivision;
    }
    public TournamentState getTournamentState() { return tournamentState; }

    // ========== Metadata convenience ==========

    public String getCareerId() { return data.getCareerId(); }
    public UUID getUserId() { return data.getUserId(); }
    public void setUserId(UUID userId) { data.setUserId(userId); }
    public UUID getUserTeamId() { return data.getUserTeamId(); }
    public void setUserTeamId(UUID id) { data.setUserTeamId(id); }
    public String getUserSessionTeamId() { return data.getUserSessionTeamId(); }
    public void setUserSessionTeamId(String id) { data.setUserSessionTeamId(id); }
    public Instant getLastUpdated() { return data.getLastUpdated(); }
    public String getDifficulty() { return data.getDifficulty(); }
    public void setDifficulty(String d) { data.setDifficulty(d); }
    public String getGameSpeed() { return data.getGameSpeed(); }
    public void setGameSpeed(String s) { data.setGameSpeed(s); }

    // ========== Convenience delegation (for backward compatibility) ==========

    public void addSessionTeam(SessionTeam team) { teamManager.addSessionTeam(team); data.touch(); }
    public SessionTeam getSessionTeam(String id) { return teamManager.getSessionTeam(id); }
    public List<SessionTeam> getAllSessionTeams() { return teamManager.getAllSessionTeams(); }

    public void addSessionPlayer(SessionPlayer player) { playerManager.addSessionPlayer(player); data.touch(); }
    public SessionPlayer getSessionPlayer(String id) { return playerManager.getSessionPlayer(id); }
    public List<SessionPlayer> getTeamSquad(String sessionTeamId) {
        return playerManager.getTeamSquad(teamManager.getSquadPlayerIds(sessionTeamId));
    }

    public void startNewSeason() { seasonManager.startNewSeason(); data.touch(); }
    public int getCurrentSeason() { return seasonManager.getCurrentSeason(); }
    public void setCurrentSeason(int season) { seasonManager.setCurrentSeason(season); }

    public Division getUserDivision() {
        String userSessionTeamId = data.getUserSessionTeamId();
        return seasonManager.findDivisionByTeamId(userSessionTeamId);
    }
    public int getTotalDivisions() { return seasonManager.getTotalDivisions(); }

    public void setPalmares(java.util.List<TournamentResult> palmares) { seasonManager.setPalmares(palmares); }
    public void addTournamentResult(TournamentResult result) { seasonManager.addTournamentResult(result); data.touch(); }

    public java.util.List<Promotion> getPromotions() { return seasonManager.getPromotions(); }
    public void updateTopTeams(TournamentResult result) { seasonManager.updateTopTeams(result); data.touch(); }

    public void executePromotionsAndRelegations() { seasonManager.executePromotionsAndRelegations(); data.touch(); }
    public List<Promotion> calculatePromotionsAndRelegations(TournamentState state) { return seasonManager.calculatePromotionsAndRelegations(state); }

    // ========== Team/Squad delegation ==========

    public List<String> getSquadPlayerIds(String sessionTeamId) { return teamManager.getSquadPlayerIds(sessionTeamId); }
    public String findSessionTeamIdByWorldTeamId(String worldTeamId) { return teamManager.findSessionTeamIdByWorldTeamId(worldTeamId); }
    public void removeSessionTeam(String id) { teamManager.removeSessionTeam(id); data.touch(); }
    public void assignPlayerToTeam(String sessionPlayerId, String sessionTeamId) {
        teamManager.assignPlayerToSquad(sessionPlayerId, sessionTeamId);
        playerManager.removeFromFreePlayers(sessionPlayerId);
        data.touch();
    }
    public void removePlayerFromTeam(String sessionPlayerId, String sessionTeamId) {
        teamManager.removePlayerFromSquad(sessionPlayerId, sessionTeamId);
        data.touch();
    }

    // ========== Player delegation ==========

    public Map<String, SessionPlayer> getSessionPlayers() { return playerManager.getSessionPlayers(); }
    public List<String> getFreePlayers() { return playerManager.getFreePlayerIds(); }
    public List<SessionPlayer> getFreePlayersObjects() { return playerManager.getFreePlayersObjects(); }
    public Map<String, Set<String>> getRemovedPlayers() { return playerManager.getRemovedPlayers(); }
    public Set<String> getRemovedPlayerIds(String worldTeamId) { return playerManager.getRemovedPlayerIds(worldTeamId); }
    public String findSessionPlayerIdByWorldPlayerId(String worldPlayerId) { return playerManager.findSessionPlayerIdByWorldPlayerId(worldPlayerId); }
    public void markPlayerAsRemoved(String worldTeamId, String worldPlayerId) { playerManager.markPlayerAsRemoved(worldTeamId, worldPlayerId); data.touch(); }
    public void removePlayer(String sessionPlayerId) {
        playerManager.removePlayer(sessionPlayerId);
        playerManager.removePlayerFromAllStarting11(teamStarting11, sessionPlayerId);
        // MVP1-lineup-cancha-1: también limpiar de subdivision map (si estaba).
        for (Map<String, String> slots : teamStarting11Subdivision.values()) {
            slots.entrySet().removeIf(e -> sessionPlayerId.equals(e.getValue()));
        }
        teamManager.removePlayerFromAllSquads(sessionPlayerId);
        data.touch();
    }
    public void addToFreePlayers(String sessionPlayerId) { playerManager.addToFreePlayers(sessionPlayerId); data.touch(); }

    // ========== Season/Division delegation ==========

    public void assignTeamsToDivisions(int teamsPerDivision) {
        // Use shared comparator from TeamOVRQueryService (same logic as Division Preview)
        seasonManager.assignTeamsToDivisions(teamManager.getAllSessionTeams(),
                TeamOVRQueryService.sessionTeamComparator(this::calculateTeamOVR),
                teamsPerDivision);
        data.touch();
    }

    private int calculateTeamOVR(String teamId) {
        List<String> playerIds = teamManager.getSquadPlayerIds(teamId);
        if (playerIds == null || playerIds.isEmpty()) {
            return 0;
        }
        int totalOVR = 0;
        int count = 0;
        for (String playerId : playerIds) {
            SessionPlayer p = playerManager.getSessionPlayer(playerId);
            if (p != null) {
                totalOVR += p.calculateOverall();
                count++;
            }
        }
        return count > 0 ? totalOVR / count : 0;
    }
}
