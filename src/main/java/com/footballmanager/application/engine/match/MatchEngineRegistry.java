package com.footballmanager.application.engine.match;

import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Registro central de motores de partido activos.
 * Wrapper que mantiene compatibilidad con la API existente.
 * Delega en MatchSessionRegistry internamente.
 */
@Component
@RequiredArgsConstructor
public class MatchEngineRegistry {

    private final MatchSessionRegistry sessionRegistry;

    /**
     * Crea e inicia un nuevo motor para un partido.
     * Si ya existe un motor para ese partido, lo retorna sin crear uno nuevo.
     */
    public synchronized MatchEngine startEngine(UUID userId, UUID matchId, UUID homeTeamId, UUID awayTeamId) {
        if (sessionRegistry.hasSession(userId, matchId)) {
            return sessionRegistry.getSession(userId, matchId)
                .map(MatchEngine::new)
                .orElse(null);
        }

        // Crear sesión (el MatchEngine se crea a partir de la sesión)
        sessionRegistry.getOrCreateSession(userId, matchId, homeTeamId, awayTeamId);

        return sessionRegistry.getSession(userId, matchId)
            .map(MatchEngine::new)
            .orElse(null);
    }

    /**
     * Obtiene un motor activo por matchId.
     */
    public Optional<MatchEngine> getEngine(UUID userId, UUID matchId) {
        return sessionRegistry.getSession(userId, matchId)
            .map(MatchEngine::new);
    }

    /**
     * Detiene y elimina un motor del registro.
     */
    public synchronized void stopAndRemoveEngine(UUID userId, UUID matchId) {
        if (sessionRegistry.hasSession(userId, matchId)) {
            sessionRegistry.getSession(userId, matchId).ifPresent(session -> {
                session.stop();
            });
            sessionRegistry.removeSession(userId, matchId);
        }
    }

    /**
     * Detiene todos los motores activos.
     */
    public synchronized void stopAllEngines() {
        // Las sesiones se detienen individualmente cuando se requieren
    }

    /**
     * Retorna el número de motores activos.
     */
    public int getActiveEngineCount() {
        return sessionRegistry.getActiveSessionCount();
    }

    /**
     * Verifica si existe un motor para un partido.
     */
    public boolean hasEngine(UUID userId, UUID matchId) {
        return sessionRegistry.hasSession(userId, matchId);
    }
}
