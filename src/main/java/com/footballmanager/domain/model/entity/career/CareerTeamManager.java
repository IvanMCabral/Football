package com.footballmanager.domain.model.entity.career;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footballmanager.domain.model.entity.SessionTeam;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gestiona los SessionTeams de una carrera.
 * Responsabilidad: operaciones CRUD y consultas sobre equipos.
 */
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class CareerTeamManager {

    private final AtomicReference<Map<String, SessionTeam>> sessionTeamsRef;
    private final AtomicReference<Map<String, List<String>>> teamSquadsRef;

    public CareerTeamManager() {
        // ConcurrentHashMap para thread-safety en contexto reactivo
        this.sessionTeamsRef = new AtomicReference<>(new ConcurrentHashMap<>());
        this.teamSquadsRef = new AtomicReference<>(new ConcurrentHashMap<>());
    }

    private Map<String, SessionTeam> sessionTeams() { return sessionTeamsRef.get(); }
    private Map<String, List<String>> teamSquads() { return teamSquadsRef.get(); }

    // Getters requeridos para serialización JSON
    @JsonProperty("sessionTeams")
    public Map<String, SessionTeam> getSessionTeams() {
        return sessionTeams();
    }

    public Map<String, List<String>> getTeamSquads() {
        return teamSquads();
    }

    // Setters requeridos para deserialización JSON
    @JsonProperty("sessionTeams")
    public synchronized void setSessionTeams(Map<String, SessionTeam> teams) {
        Map<String, SessionTeam> newMap = new ConcurrentHashMap<>();
        if (teams != null) {
            newMap.putAll(teams);
        }
        this.sessionTeamsRef.set(newMap);
    }

    @JsonProperty("teamSquads")
    public synchronized void setTeamSquads(Map<String, List<String>> squads) {
        Map<String, List<String>> newMap = new ConcurrentHashMap<>();
        if (squads != null) {
            squads.forEach((k, v) -> {
                newMap.put(k, v != null ? new ArrayList<>(v) : new ArrayList<>());
            });
        }
        Map<String, List<String>> currentSquads = teamSquadsRef.get();
        currentSquads.clear();
        currentSquads.putAll(newMap);
    }

    /**
     * Agrega un SessionTeam al save
     */
    public void addSessionTeam(SessionTeam team) {
        sessionTeams().put(team.getSessionTeamId(), team);
        teamSquads().computeIfAbsent(team.getSessionTeamId(), k -> new ArrayList<>());
    }

    /**
     * Obtiene un SessionTeam por ID
     */
    public SessionTeam getSessionTeam(String sessionTeamId) {
        return sessionTeams().get(sessionTeamId);
    }

    /**
     * Obtiene todos los SessionTeams
     */
    public List<SessionTeam> getAllSessionTeams() {
        return new ArrayList<>(sessionTeams().values());
    }

    /**
     * Elimina un SessionTeam y sus jugadores asociados
     */
    public List<String> removeSessionTeam(String sessionTeamId) {
        sessionTeams().remove(sessionTeamId);
        return teamSquads().remove(sessionTeamId);
    }

    /**
     * Busca el sessionTeamId de un SessionTeam por su worldTeamId
     */
    public String findSessionTeamIdByWorldTeamId(String worldTeamId) {
        return sessionTeams().values().stream()
                .filter(team -> worldTeamId.equals(team.getWorldTeamId()))
                .map(SessionTeam::getSessionTeamId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Asigna un jugador al squad de un equipo
     */
    public void assignPlayerToSquad(String sessionPlayerId, String sessionTeamId) {
        teamSquads().computeIfAbsent(sessionTeamId, k -> new ArrayList<>()).add(sessionPlayerId);
    }

    /**
     * Remueve un jugador del squad de un equipo
     */
    public boolean removePlayerFromSquad(String sessionPlayerId, String sessionTeamId) {
        List<String> squad = teamSquads().get(sessionTeamId);
        return squad != null && squad.remove(sessionPlayerId);
    }

    /**
     * Obtiene las IDs del squad de un equipo
     */
    public List<String> getSquadPlayerIds(String sessionTeamId) {
        return teamSquads().getOrDefault(sessionTeamId, Collections.emptyList());
    }

    /**
     * Actualiza el squad completo de un equipo
     */
    public void setSquad(String sessionTeamId, List<String> playerIds) {
        teamSquads().put(sessionTeamId, new ArrayList<>(playerIds));
    }

    /**
     * Elimina jugador de todos los squads
     */
    public void removePlayerFromAllSquads(String sessionPlayerId) {
        for (List<String> squad : teamSquads().values()) {
            squad.remove(sessionPlayerId);
        }
    }

    /**
     * Calcula el OVR promedio de un equipo
     */
    public int calculateTeamOVR(String sessionTeamId, java.util.function.Function<String, com.footballmanager.domain.model.entity.SessionPlayer> playerProvider) {
        List<String> playerIds = teamSquads().getOrDefault(sessionTeamId, Collections.emptyList());
        if (playerIds.isEmpty()) return 0;
        return (int) playerIds.stream()
            .mapToInt(pid -> {
                com.footballmanager.domain.model.entity.SessionPlayer p = playerProvider.apply(pid);
                return p != null ? p.calculateOverall() : 0;
            })
            .average()
            .orElse(0);
    }

    /**
     * Obtiene todos los sessionTeamIds
     */
    public Set<String> getAllSessionTeamIds() {
        return new HashSet<>(sessionTeams().keySet());
    }
}
