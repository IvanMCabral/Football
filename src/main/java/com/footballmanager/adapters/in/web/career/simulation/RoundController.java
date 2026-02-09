package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.MatchManagementService;
import com.footballmanager.application.service.simulation.MatchResultProcessor;
import com.footballmanager.application.service.simulation.MatchSimulationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller for round operations (start round).
 * Uses non-blocking reactive patterns.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/match-engine/rounds")
@RequiredArgsConstructor
public class RoundController {

    private final MatchManagementService matchManagementService;
    private final MatchEngineRegistry engineRegistry;
    private final RoundEngineRegistry roundEngineRegistry;
    private final MatchSimulationOrchestrator orchestrator;

    /**
     * POST /api/v1/match-engine/rounds/start
     * Starts a new round with multiple matches.
     * Returns immediately while round starts asynchronously.
     */
    @PostMapping(value = "/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<RoundState>> startRound(@RequestBody StartRoundRequest request, Authentication authentication) {
        UUID roundId = UUID.fromString(request.roundId());
        UUID userId = getUserIdFromAuth(authentication, request.userId());

        log.info("[ROUND-CONTROLLER] Starting round {} for user {}", roundId, userId);

        // NO invalidar cache aquí - el orquestador maneja la escritura
        // MatchSimulationOrchestrator es la ÚNICA autoridad para careerPhase

        // Continuar con el inicio de la ronda
        return startMatches(roundId, userId, request)
            .map(initialState -> ResponseEntity.ok(initialState))
            .onErrorResume(e -> {
                log.error("[ROUND-CONTROLLER] Error starting round {}: {}", request.roundId(), e.getMessage(), e);
                return Mono.just(ResponseEntity.internalServerError().build());
            });
    }

    private Mono<RoundState> startMatches(UUID roundId, UUID userId, StartRoundRequest request) {
        RoundEngine roundEngine = new RoundEngine(roundId);
        log.info("[ROUND-CONTROLLER] Created RoundEngine for roundId: {}", roundId);

        List<UUID> matchIds = new ArrayList<>();
        final int totalMatches = request.matches().size();
        final AtomicInteger matchesFinished = new AtomicInteger(0);
        final List<MatchResultProcessor.MatchResultInfo> matchResults =
                Collections.synchronizedList(new ArrayList<>());

        // Iniciar todos los partidos
        for (StartRoundRequest.MatchInfo matchInfo : request.matches()) {
            UUID matchId = UUID.fromString(matchInfo.matchId());
            UUID homeTeamId = UUID.fromString(matchInfo.homeTeamId());
            UUID awayTeamId = UUID.fromString(matchInfo.awayTeamId());

            matchIds.add(matchId);
            log.info("[ROUND-CONTROLLER] Processing match: {}", matchId);

            // Iniciar partido
            matchManagementService.startMatch(
                    userId,
                    matchId,
                    homeTeamId,
                    awayTeamId,
                    finalState -> {
                        matchResults.add(new MatchResultProcessor.MatchResultInfo(
                                matchId.toString(),
                                finalState.score().home(),
                                finalState.score().away()
                        ));

                        int finished = matchesFinished.incrementAndGet();
                        log.info("[ROUND-CONTROLLER] Match {} finished, {}/{} total", matchId, finished, totalMatches);

                        if (finished == totalMatches) {
                            log.info("[ROUND-CONTROLLER] All matches finished, emitting completed state");
                            roundEngine.emitCompletedState();
                            // Procesar resultados de forma asíncrona, sin bloquear
                            orchestrator.processMatchDayResults(userId.toString(), matchResults)
                                    .subscribe();
                        }
                    })
                    .subscribe();

            MatchEngine matchEngine = engineRegistry.startEngine(userId, matchId, homeTeamId, awayTeamId);
            log.info("[ROUND-CONTROLLER] Got MatchEngine for match {}: {}", matchId, matchEngine != null ? "OK" : "NULL");
            roundEngine.registerMatch(matchId, matchEngine);
        }

        roundEngineRegistry.register(roundId, roundEngine);
        log.info("[ROUND-CONTROLLER] Registered round engine, calling start()");
        roundEngine.start();
        log.info("[ROUND-CONTROLLER] Round engine start() called, isRunning: {}", roundEngine.isRunning());

        // Construir estado inicial
        return Flux.fromIterable(matchIds)
            .flatMap(matchId -> matchManagementService.getMatchState(userId, matchId))
            .collectList()
            .map(matchStates -> {
                RoundState initialState = new RoundState(
                        roundId,
                        java.time.Instant.now(),
                        matchStates,
                        RoundState.RoundStatus.IN_PROGRESS
                );
                log.info("[ROUND-CONTROLLER] Returning initial state with {} matches", matchStates.size());
                return initialState;
            });
    }

    public record StartRoundRequest(String roundId, String userId, List<MatchInfo> matches) {
        public record MatchInfo(String matchId, String homeTeamId, String awayTeamId) {}
    }

    private UUID getUserIdFromAuth(Authentication authentication, String requestUserId) {
        if (requestUserId != null) {
            return UUID.fromString(requestUserId);
        }
        if (authentication != null && authentication.getName() != null) {
            return UUID.fromString(authentication.getName());
        }
        throw new IllegalArgumentException("User ID not available from authentication or request");
    }
}
