package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
    /** Legacy worldPlayerId -> canonical worldPlayerId, owner-scoped in V2 overlays. */
    private Map<String, String> worldPlayerAliases;
    private Instant createdAt;
    private Instant lastUpdated;
    
    public WorldSnapshot() {
        this.leagues = new ArrayList<>();
        this.worldTeams = new HashMap<>();
        this.worldPlayers = new HashMap<>();
        this.worldPlayerAliases = new HashMap<>();
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
        WorldPlayer direct = worldPlayers.get(worldPlayerId);
        if (direct != null) return direct;
        String canonicalId = worldPlayerAliases == null ? null : worldPlayerAliases.get(worldPlayerId);
        return canonicalId == null ? null : worldPlayers.get(canonicalId);
    }
    
    /**
     * Obtiene todos los WorldTeams
     */
    @JsonIgnore
    public List<WorldTeam> getAllWorldTeams() {
        return new ArrayList<>(worldTeams.values());
    }
    
    /**
     * Obtiene todos los WorldPlayers
     */
    @JsonIgnore
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

    public Map<String, String> getWorldPlayerAliases() {
        return worldPlayerAliases == null ? Collections.emptyMap() : Collections.unmodifiableMap(worldPlayerAliases);
    }

    public void setWorldPlayerAliases(Map<String, String> aliases) {
        this.worldPlayerAliases = aliases == null ? new HashMap<>() : new HashMap<>(aliases);
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
