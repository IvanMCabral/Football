package com.footballmanager.application.service.match;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchFinishedResult;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.port.in.match.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Servicio de orquestación para gestión de partidos.
 * Delega en los UseCases sin contener lógica de negocio propia.
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
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

        return Flux.error(new IllegalStateException("match start requires career lifecycle context"));
    }

    public Flux<MatchStateSnapshot> startMatch(
            UUID userId,
            UUID matchId,
            UUID homeTeamId,
            UUID awayTeamId,
            String careerId,
            Consumer<MatchStateSnapshot> onFinishCallback) {

        if (careerId == null || careerId.isBlank()) {
            return Flux.error(new IllegalStateException("match start requires career lifecycle context"));
        }
        return Flux.error(new IllegalStateException("match start requires lifecycle generation"));
    }

    public Flux<MatchStateSnapshot> startMatch(UUID userId, UUID matchId, UUID homeTeamId,
                                                 UUID awayTeamId, String careerId,
                                                 String lifecycleGeneration,
                                                 Consumer<MatchStateSnapshot> onFinishCallback) {
        if (careerId == null || careerId.isBlank() || lifecycleGeneration == null || lifecycleGeneration.isBlank()) {
            return Flux.error(new IllegalStateException("match start requires career lifecycle context"));
        }
        sessionRegistry.getOrCreateSession(userId, matchId, homeTeamId, awayTeamId,
                careerId, lifecycleGeneration);
        return startMatchUseCase.execute(userId, matchId, onFinishCallback);
    }
    public Flux<MatchStateSnapshot> startMatch(
            UUID userId,
            UUID matchId,
            UUID homeTeamId,
            UUID awayTeamId,
            Consumer<MatchFinishedResult> onFinishCallback,
            LiveSession detailedMatchSession) {

        return Flux.error(new IllegalStateException("match start requires career lifecycle context"));
    }

    public Flux<MatchStateSnapshot> startMatch(
            UUID userId,
            UUID matchId,
            UUID homeTeamId,
            UUID awayTeamId,
            String careerId,
            Consumer<MatchFinishedResult> onFinishCallback,
            LiveSession detailedMatchSession) {

        if (careerId == null || careerId.isBlank()) {
            return Flux.error(new IllegalStateException("match start requires career lifecycle context"));
        }
        return Flux.error(new IllegalStateException("match start requires lifecycle generation"));
    }

    public Flux<MatchStateSnapshot> startMatch(UUID userId, UUID matchId, UUID homeTeamId,
                                                UUID awayTeamId, String careerId,
                                                String lifecycleGeneration,
                                                Consumer<MatchFinishedResult> onFinishCallback,
                                                LiveSession detailedMatchSession) {
        if (careerId == null || careerId.isBlank() || lifecycleGeneration == null || lifecycleGeneration.isBlank()) {
            return Flux.error(new IllegalStateException("match start requires career lifecycle context"));
        }
        sessionRegistry.getOrCreateDetailedSession(userId, matchId, homeTeamId, awayTeamId,
                careerId, lifecycleGeneration, detailedMatchSession);
        return startMatchUseCaseImpl.executeDetailedMatch(userId, matchId, onFinishCallback,
                detailedMatchSession);
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
        log.debug("resumeMatch requested userId={}, matchId={}", userId, matchId);

        return resumeMatchUseCase.execute(userId, matchId)
            .doOnSuccess(v -> {
                var roundEngine = roundEngineRegistry.getByMatchId(matchId);
                if (roundEngine != null) {
                    roundEngine.resumeAll();
                    log.debug("RoundEngine resumed for matchId={}", matchId);
                } else {
                    log.debug("No RoundEngine registered for matchId={}", matchId);
                }
            })
            .doOnError(e -> log.warn("Could not resume match matchId={}", matchId, e));
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
