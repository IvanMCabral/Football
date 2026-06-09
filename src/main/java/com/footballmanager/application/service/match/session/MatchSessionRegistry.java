package com.footballmanager.application.service.match.session;

import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.domain.model.entity.MatchState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de sesiones de partido activas.
 * Thread-safe usando ConcurrentHashMap.
 *
 * <p>V24D6M11: Supports creating MatchSession with V24LiveSession for
 * V24DetailedMatchEngine path.
 */
@Component
public class MatchSessionRegistry {

    private final Map<String, MatchSession> activeSessions = new ConcurrentHashMap<>();
    private final MatchTickHandler tickHandler;

    public MatchSessionRegistry(MatchTickHandler tickHandler) {
        this.tickHandler = tickHandler;
    }

    private String buildKey(UUID userId, UUID matchId) {
        return userId.toString() + ":" + matchId.toString();
    }

    /**
     * Obtiene una sesión existente o crea una nueva si no existe (legacy path).
     */
    public Optional<MatchSession> getOrCreateSession(UUID userId, UUID matchId, UUID homeTeamId, UUID awayTeamId) {
        String key = buildKey(userId, matchId);
        return Optional.ofNullable(activeSessions.computeIfAbsent(key, id -> {
            MatchState initialState = new MatchState(matchId);
            initialState.setHomeTeamId(homeTeamId);
            initialState.setAwayTeamId(awayTeamId);
            return new MatchSession(userId, matchId, initialState, tickHandler);
        }));
    }

    /**
     * V24D6M11: Obtiene o crea una sesión con V24LiveSession (V24 path).
     */
    public MatchSession getOrCreateSessionWithV24(UUID userId, UUID matchId, UUID homeTeamId, UUID awayTeamId, V24LiveSession v24LiveSession) {
        String key = buildKey(userId, matchId);
        return activeSessions.computeIfAbsent(key, id -> {
            MatchState initialState = new MatchState(matchId);
            initialState.setHomeTeamId(homeTeamId);
            initialState.setAwayTeamId(awayTeamId);
            return new MatchSession(userId, matchId, initialState, tickHandler, v24LiveSession);
        });
    }

    /**
     * Obtiene una sesión existente sin crear una nueva.
     */
    public Optional<MatchSession> getSession(UUID userId, UUID matchId) {
        String key = buildKey(userId, matchId);
        return Optional.ofNullable(activeSessions.get(key));
    }

    /**
     * Elimina una sesión del registro.
     */
    public void removeSession(UUID userId, UUID matchId) {
        String key = buildKey(userId, matchId);
        activeSessions.remove(key);
    }

    /**
     * Retorna el número de sesiones activas.
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Verifica si existe una sesión para un partido.
     */
    public boolean hasSession(UUID userId, UUID matchId) {
        String key = buildKey(userId, matchId);
        return activeSessions.containsKey(key);
    }

    /**
     * Detiene todas las sesiones activas y limpia el registro.
     * Usado cuando se elimina una carrera.
     */
    public void clearAllSessions() {
        System.out.println("[MATCH-REGISTRY] Clearing ALL sessions, count: " + activeSessions.size());
        activeSessions.values().forEach(session -> {
            try {
                session.stop();
            } catch (Exception e) {
                System.out.println("[MATCH-REGISTRY] Error stopping session: " + e.getMessage());
            }
        });
        activeSessions.clear();
        System.out.println("[MATCH-REGISTRY] All sessions stopped and cleared");
    }
}
