package com.footballmanager.application.service.match.session;

import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.domain.model.entity.MatchState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de sesiones de partido activas.
 * Thread-safe usando ConcurrentHashMap.
 *
 */
@Component
@Slf4j
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
        return getOrCreateSession(userId, matchId, homeTeamId, awayTeamId, null);
    }

    public Optional<MatchSession> getOrCreateSession(UUID userId, UUID matchId, UUID homeTeamId,
                                                      UUID awayTeamId, String careerId) {
        return getOrCreateSession(userId, matchId, homeTeamId, awayTeamId, careerId, null);
    }

    public Optional<MatchSession> getOrCreateSession(UUID userId, UUID matchId, UUID homeTeamId,
                                                      UUID awayTeamId, String careerId,
                                                      String lifecycleGeneration) {
        String key = buildKey(userId, matchId);
        return Optional.ofNullable(activeSessions.computeIfAbsent(key, id -> {
            MatchState initialState = new MatchState(matchId, userId, careerId, lifecycleGeneration);
            initialState.setHomeTeamId(homeTeamId);
            initialState.setAwayTeamId(awayTeamId);
            return new MatchSession(userId, matchId, initialState, tickHandler, null, lifecycleGeneration);
        }));
    }
    public MatchSession getOrCreateDetailedSession(UUID userId, UUID matchId, UUID homeTeamId, UUID awayTeamId, LiveSession detailedMatchSession) {
        return getOrCreateDetailedSession(userId, matchId, homeTeamId, awayTeamId, null, detailedMatchSession);
    }

    public MatchSession getOrCreateDetailedSession(UUID userId, UUID matchId, UUID homeTeamId,
                                                    UUID awayTeamId, String careerId,
                                                    LiveSession detailedMatchSession) {
        return getOrCreateDetailedSession(userId, matchId, homeTeamId, awayTeamId,
                careerId, null, detailedMatchSession);
    }

    public MatchSession getOrCreateDetailedSession(UUID userId, UUID matchId, UUID homeTeamId,
                                                    UUID awayTeamId, String careerId,
                                                    String lifecycleGeneration,
                                                    LiveSession detailedMatchSession) {
        String key = buildKey(userId, matchId);
        return activeSessions.computeIfAbsent(key, id -> {
            MatchState initialState = new MatchState(matchId, userId, careerId, lifecycleGeneration);
            // downstream MatchStateSnapshot carries it (used by
            // RoundController.persistFinishedMatch as a secondary fallback
            // for the userId namespace). Without this, the detailed match path
            // produced a snapshot with userId=null, forcing
            // extractUserIdForMatchPersistence to fall back to
            // UUID.randomUUID() — which persisted the match under an
            // orphan key that GET /api/v1/matches could never find.
            initialState.setHomeTeamId(homeTeamId);
            initialState.setAwayTeamId(awayTeamId);
            initialState.setCareerId(careerId);
            return new MatchSession(userId, matchId, initialState, tickHandler,
                    detailedMatchSession, lifecycleGeneration);
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

    /** Stops and removes only sessions owned by the requested account/career. */
    public int clearSessionsForOwner(UUID userId, String careerId) {
        int cleared = 0;
        for (Map.Entry<String, MatchSession> entry : activeSessions.entrySet()) {
            MatchSession session = entry.getValue();
            if (session.belongsTo(userId, careerId) && activeSessions.remove(entry.getKey(), session)) {
                try {
                    session.stop();
                } catch (Exception e) {
                    log.warn("Error stopping owner-scoped match session", e);
                }
                cleared++;
            }
        }
        return cleared;
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
        log.debug("Clearing active match sessions count={}", activeSessions.size());
        activeSessions.values().forEach(session -> {
            try {
                session.stop();
            } catch (Exception e) {
                log.warn("Error stopping match session", e);
            }
        });
        activeSessions.clear();
        log.debug("Active match sessions cleared");
    }
}
