package com.footballmanager.application.service.match;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.port.in.match.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Servicio de orquestación para gestión de partidos.
 * Delega en los UseCases sin contener lógica de negocio propia.
 *
 * Cumple el principio de composición: coordina el flujo entre UseCases.
 */
@Service
@RequiredArgsConstructor
public class MatchManagementService {

    private final StartMatchUseCase startMatchUseCase;
    private final PauseMatchUseCase pauseMatchUseCase;
    private final ResumeMatchUseCase resumeMatchUseCase;
    private final StopMatchUseCase stopMatchUseCase;
    private final ExecuteMatchCommandUseCase executeMatchCommandUseCase;
    private final MatchSessionRegistry sessionRegistry;
    private final RoundEngineRegistry roundEngineRegistry;

    /**
     * Inicia la simulación de un partido.
     */
    public Flux<MatchStateSnapshot> startMatch(
            UUID userId,
            UUID matchId,
            UUID homeTeamId,
            UUID awayTeamId,
            Consumer<MatchStateSnapshot> onFinishCallback) {

        // Asegurar que existe la sesión antes de iniciar
        sessionRegistry.getOrCreateSession(userId, matchId, homeTeamId, awayTeamId);

        return startMatchUseCase.execute(userId, matchId, onFinishCallback);
    }

    /**
     * Pausa un partido en curso.
     */
    public Mono<Void> pauseMatch(UUID userId, UUID matchId) {
        return pauseMatchUseCase.execute(userId, matchId);
    }

    /**
     * Reanuda un partido pausado.
     * También reanuda el RoundEngine asociado.
     */
    public Mono<Void> resumeMatch(UUID userId, UUID matchId) {
        System.out.println("[MATCH-MGMT] resumeMatch called - userId: " + userId + ", matchId: " + matchId);

        return resumeMatchUseCase.execute(userId, matchId)
            .doOnSuccess(v -> {
                System.out.println("[MATCH-MGMT] UseCase.execute success, looking for RoundEngine...");
                // Reanudar el RoundEngine que contiene este partido
                var roundEngine = roundEngineRegistry.getByMatchId(matchId);
                if (roundEngine != null) {
                    System.out.println("[MATCH-MGMT] Found RoundEngine, calling resumeAll()...");
                    roundEngine.resumeAll();
                    System.out.println("[MATCH-MGMT] RoundEngine.resumeAll() called successfully for matchId: " + matchId);
                } else {
                    System.out.println("[MATCH-MGMT] RoundEngine NOT FOUND for matchId: " + matchId);
                }
            })
            .doOnError(e -> {
                System.out.println("[MATCH-MGMT] UseCase.execute ERROR: " + e.getMessage());
                e.printStackTrace();
            });
    }

    /**
     * Detiene un partido.
     */
    public Mono<Void> stopMatch(UUID userId, UUID matchId) {
        return stopMatchUseCase.execute(userId, matchId);
    }

    /**
     * Ejecuta un comando táctico.
     */
    public Mono<Boolean> executeCommand(UUID userId, UUID matchId, MatchCommand command) {
        return executeMatchCommandUseCase.execute(userId, matchId, command);
    }

    /**
     * Obtiene el estado actual de un partido.
     */
    public Mono<MatchStateSnapshot> getMatchState(UUID userId, UUID matchId) {
        return sessionRegistry.getSession(userId, matchId)
            .map(session -> session.getCurrentState())
            .map(Mono::just)
            .orElseGet(Mono::empty);
    }
}
