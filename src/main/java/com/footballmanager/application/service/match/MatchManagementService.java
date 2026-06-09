package com.footballmanager.application.service.match;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.session.MatchSession;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchFinishedResult;
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
 * <p>V24D6M11: Added startMatch with V24LiveSession for V24DetailedMatchEngine path.
 */
@Service
@RequiredArgsConstructor
public class MatchManagementService {

    private final StartMatchUseCase startMatchUseCase;
    private final StartMatchUseCaseImpl startMatchUseCaseImpl;
    private final PauseMatchUseCase pauseMatchUseCase;
    private final ResumeMatchUseCase resumeMatchUseCase;
    private final StopMatchUseCase stopMatchUseCase;
    private final ExecuteMatchCommandUseCase executeMatchCommandUseCase;
    private final MatchSessionRegistry sessionRegistry;
    private final RoundEngineRegistry roundEngineRegistry;

    /**
     * Inicia la simulación de un partido (legacy path).
     */
    public Flux<MatchStateSnapshot> startMatch(
            UUID userId,
            UUID matchId,
            UUID homeTeamId,
            UUID awayTeamId,
            Consumer<MatchStateSnapshot> onFinishCallback) {

        sessionRegistry.getOrCreateSession(userId, matchId, homeTeamId, awayTeamId);
        return startMatchUseCase.execute(userId, matchId, onFinishCallback);
    }

    /**
     * V24D6M11: Inicia la simulación de un partido con V24LiveSession.
     * Uses executeV24 with MatchFinishedResult callback for V24DetailedMatchEngine path.
     */
    public Flux<MatchStateSnapshot> startMatch(
            UUID userId,
            UUID matchId,
            UUID homeTeamId,
            UUID awayTeamId,
            Consumer<MatchFinishedResult> onFinishCallback,
            V24LiveSession v24LiveSession) {

        // Create session with V24LiveSession
        MatchSession session = sessionRegistry.getOrCreateSessionWithV24(
                userId, matchId, homeTeamId, awayTeamId, v24LiveSession);

        return startMatchUseCaseImpl.executeV24(userId, matchId, onFinishCallback, v24LiveSession);
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
