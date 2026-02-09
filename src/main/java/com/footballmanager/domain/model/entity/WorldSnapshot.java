package com.footballmanager.domain.model.entity;

import java.time.Instant;
import java.util.*;

/**
 * WorldSnapshot - Vista del mundo para un usuario específico.
 * Contiene TODA la data que consume el frontend (reales + custom).
 * Se crea UNA VEZ al login y luego solo se incrementa.
 * 
 * NO se reconstruye en cada login.
 */
public class WorldSnapshot {
    
    private UUID userId;
    private List<WorldLeague> leagues;
    private Map<String, WorldTeam> worldTeams;        // key: worldTeamId
    private Map<String, WorldPlayer> worldPlayers;    // key: worldPlayerId
    private Instant createdAt;
    private Instant lastUpdated;
    
    public WorldSnapshot() {
        this.leagues = new ArrayList<>();
        this.worldTeams = new HashMap<>();
        this.worldPlayers = new HashMap<>();
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }
    
    // ========== Métodos de negocio ==========
    
    /**
     * Agrega un WorldTeam al snapshot
     */
    public void addWorldTeam(WorldTeam team) {
        this.worldTeams.put(team.getWorldTeamId(), team);
        this.lastUpdated = Instant.now();
    }
    
    /**
     * Agrega un WorldPlayer al snapshot
     */
    public void addWorldPlayer(WorldPlayer player) {
        this.worldPlayers.put(player.getWorldPlayerId(), player);
        this.lastUpdated = Instant.now();
    }
    
    /**
     * Obtiene todos los WorldTeams de una liga
     */
    public List<WorldTeam> getTeamsByLeague(UUID leagueId) {
        return worldTeams.values().stream()
                .filter(team -> leagueId.equals(team.getRealLeagueId()))
                .toList();
    }
    
    /**
     * Obtiene todos los WorldPlayers de un WorldTeam
     */
    public List<WorldPlayer> getPlayersByWorldTeam(String worldTeamId) {
        return worldPlayers.values().stream()
                .filter(player -> worldTeamId.equals(player.getWorldTeamId()))
                .toList();
    }
    
    /**
     * Obtiene un WorldTeam por ID
     */
    public WorldTeam getWorldTeam(String worldTeamId) {
        return worldTeams.get(worldTeamId);
    }
    
    /**
     * Obtiene un WorldPlayer por ID
     */
    public WorldPlayer getWorldPlayer(String worldPlayerId) {
        return worldPlayers.get(worldPlayerId);
    }
    
    /**
     * Obtiene todos los WorldTeams
     */
    public List<WorldTeam> getAllWorldTeams() {
        return new ArrayList<>(worldTeams.values());
    }
    
    /**
     * Obtiene todos los WorldPlayers
     */
    public List<WorldPlayer> getAllWorldPlayers() {
        return new ArrayList<>(worldPlayers.values());
    }
    
    // ========== Getters y Setters ==========
    
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public List<WorldLeague> getLeagues() {
        return leagues;
    }

    public void setLeagues(List<WorldLeague> leagues) {
        this.leagues = leagues;
    }

    public Map<String, WorldTeam> getWorldTeams() {
        return worldTeams;
    }

    public void setWorldTeams(Map<String, WorldTeam> worldTeams) {
        this.worldTeams = worldTeams;
    }

    public Map<String, WorldPlayer> getWorldPlayers() {
        return worldPlayers;
    }

    public void setWorldPlayers(Map<String, WorldPlayer> worldPlayers) {
        this.worldPlayers = worldPlayers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
