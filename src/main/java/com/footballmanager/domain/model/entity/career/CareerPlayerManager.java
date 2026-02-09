package com.footballmanager.domain.model.entity.career;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footballmanager.domain.model.entity.SessionPlayer;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Gestiona los SessionPlayers de una carrera.
 * Responsabilidad: operaciones CRUD y consultas sobre jugadores.
 */
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class CareerPlayerManager {

    private final AtomicReference<Map<String, SessionPlayer>> sessionPlayersRef;
    private final AtomicReference<List<String>> freePlayersRef;
    private final AtomicReference<Map<String, Set<String>>> removedPlayersRef;

    public CareerPlayerManager() {
        // ConcurrentHashMap para thread-safety en contexto reactivo
        this.sessionPlayersRef = new AtomicReference<>(new ConcurrentHashMap<>());
        this.freePlayersRef = new AtomicReference<>(new ArrayList<>());
        this.removedPlayersRef = new AtomicReference<>(new ConcurrentHashMap<>());
    }

    private Map<String, SessionPlayer> sessionPlayers() { return sessionPlayersRef.get(); }
    private List<String> freePlayers() { return freePlayersRef.get(); }
    private Map<String, Set<String>> removedPlayers() { return removedPlayersRef.get(); }

    // Setters requeridos para deserialización JSON
    @JsonProperty("sessionPlayers")
    public void setSessionPlayers(Map<String, SessionPlayer> players) {
        Map<String, SessionPlayer> newMap = new ConcurrentHashMap<>();
        if (players != null) {
            newMap.putAll(players);
        }
        this.sessionPlayersRef.set(newMap);
    }

    @JsonProperty("freePlayers")
    public void setFreePlayers(List<String> players) {
        List<String> newList = new ArrayList<>();
        if (players != null) {
            newList.addAll(players);
        }
        this.freePlayersRef.set(newList);
    }

    /**
     * Obtiene el mapa de todos los jugadores
     */
    @JsonProperty("sessionPlayers")
    public Map<String, SessionPlayer> getSessionPlayers() {
        return sessionPlayers();
    }

    /**
     * Itera sobre todos los jugadores
     */
    public void forEachPlayer(BiConsumer<String, SessionPlayer> action) {
        sessionPlayers().forEach(action);
    }

    /**
     * Agrega un SessionPlayer al save
     */
    public void addSessionPlayer(SessionPlayer player) {
        sessionPlayers().put(player.getSessionPlayerId(), player);
    }

    /**
     * Obtiene un SessionPlayer por ID
     */
    public SessionPlayer getSessionPlayer(String sessionPlayerId) {
        return sessionPlayers().get(sessionPlayerId);
    }

    /**
     * Remueve un jugador completamente de la carrera
     */
    public void removePlayer(String sessionPlayerId) {
        sessionPlayers().remove(sessionPlayerId);
        freePlayers().remove(sessionPlayerId);
    }

    /**
     * Busca el sessionPlayerId de un SessionPlayer por su worldPlayerId
     */
    public String findSessionPlayerIdByWorldPlayerId(String worldPlayerId) {
        return sessionPlayers().values().stream()
                .filter(player -> worldPlayerId.equals(player.getWorldPlayerId()))
                .map(SessionPlayer::getSessionPlayerId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Obtiene el squad de un equipo como objetos SessionPlayer
     */
    public List<SessionPlayer> getTeamSquad(List<String> playerIds) {
        log.debug("[SQUAD-DUP] CareerPlayerManager.getTeamSquad - looking up {} playerIds", playerIds != null ? playerIds.size() : "null");
        List<SessionPlayer> squad = new ArrayList<>();
        if (playerIds == null) {
            log.debug("[SQUAD-DUP]   playerIds is null, returning empty squad");
            return squad;
        }
        for (String playerId : playerIds) {
            SessionPlayer player = sessionPlayers().get(playerId);
            if (player != null) {
                squad.add(player);
            }
        }
        log.debug("[SQUAD-DUP]   Found {} players in sessionPlayers map", squad.size());
        return squad;
    }

    /**
     * Obtiene los free players como objetos SessionPlayer
     */
    public List<SessionPlayer> getFreePlayersObjects() {
        List<SessionPlayer> players = new ArrayList<>();
        for (String playerId : freePlayers()) {
            SessionPlayer player = sessionPlayers().get(playerId);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    /**
     * Agrega un jugador a la lista de free players
     */
    public void addToFreePlayers(String sessionPlayerId) {
        List<String> current = freePlayers();
        if (!current.contains(sessionPlayerId)) {
            List<String> newList = new ArrayList<>(current);
            newList.add(sessionPlayerId);
            freePlayersRef.set(newList);
        }
    }

    /**
     * Remueve un jugador de la lista de free players
     */
    public void removeFromFreePlayers(String sessionPlayerId) {
        List<String> current = freePlayers();
        List<String> newList = new ArrayList<>(current);
        newList.remove(sessionPlayerId);
        freePlayersRef.set(newList);
    }

    /**
     * Obtiene las IDs de free players
     */
    @JsonProperty("freePlayers")
    public List<String> getFreePlayerIds() {
        return new ArrayList<>(freePlayers());
    }

    /**
     * Verifica si un jugador está en free players
     */
    public boolean isFreePlayer(String sessionPlayerId) {
        return freePlayers().contains(sessionPlayerId);
    }

    // ========== Métodos para jugadores removidos (solo referencias) ==========

    @JsonProperty("removedPlayers")
    public Map<String, Set<String>> getRemovedPlayers() {
        return removedPlayers();
    }

    public void setRemovedPlayers(Map<String, Set<String>> removedPlayers) {
        Map<String, Set<String>> newMap = new ConcurrentHashMap<>();
        if (removedPlayers != null) {
            removedPlayers.forEach((k, v) -> newMap.put(k, new HashSet<>(v)));
        }
        this.removedPlayersRef.set(newMap);
    }

    /**
     * Marca un worldPlayerId como removido de un worldTeamId
     */
    public void markPlayerAsRemoved(String worldTeamId, String worldPlayerId) {
        removedPlayers().computeIfAbsent(worldTeamId, k -> new HashSet<>()).add(worldPlayerId);
    }

    /**
     * Verifica si un worldPlayerId está marcado como removido
     */
    public boolean isPlayerRemoved(String worldTeamId, String worldPlayerId) {
        Set<String> removed = removedPlayers().get(worldTeamId);
        return removed != null && removed.contains(worldPlayerId);
    }

    /**
     * Obtiene todos los worldPlayerIds removidos de un equipo
     */
    public Set<String> getRemovedPlayerIds(String worldTeamId) {
        Set<String> removed = removedPlayers().get(worldTeamId);
        return removed != null ? new HashSet<>(removed) : new HashSet<>();
    }

    /**
     * Remueve jugador de starting 11 de todos los equipos
     */
    public void removePlayerFromAllStarting11(Map<String, List<String>> teamStarting11, String sessionPlayerId) {
        for (List<String> starting11 : teamStarting11.values()) {
            starting11.remove(sessionPlayerId);
        }
    }
}
