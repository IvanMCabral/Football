package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.application.engine.match.MatchEngine;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.match.MatchManagementService;
import com.footballmanager.application.service.simulation.LeagueSimulator;
import com.footballmanager.application.service.simulation.MatchResultProcessor;
import com.footballmanager.application.service.simulation.MatchSimulationOrchestrator;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchFinishedResult;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>V24D6M11: When simulation.use-v24-detailed-engine=true, matches use
 * V24DetailedMatchEngine via V24LiveSession for real player attribution
 * and per-minute SSE tick-by-tick simulation.
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
    private final CareerSessionService careerSessionService;
    private final V24MatchContextFactory v24ContextFactory;
    private final LeagueSimulator leagueSimulator;

    /** V24D6M11: When true, matches use V24DetailedMatchEngine via V24LiveSession. */
    @Value("${simulation.use-v24-detailed-engine:true}")
    private boolean useV24DetailedEngine;

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

        // V24D6M11: Load CareerSave once for all matches — needed for V24MatchContext construction
        CareerSave career = careerSessionService.getCareerFromCache(userId).block();
        if (career == null) {
            log.error("[ROUND-CONTROLLER] CareerSave not found for user {}", userId);
            return Mono.error(new IllegalStateException("Career not found for user: " + userId));
        }
        log.info("[ROUND-CONTROLLER] CareerSave loaded for V24 context construction");
        // [V24D6M11-TRACE] Log careerId for E2E tracing
        String traceCareerId = career.getData().getCareerId();
        log.info("[V24D6M11-TRACE] RoundController careerId={}, roundId={}", traceCareerId, roundId);

        // Iniciar todos los partidos
        for (StartRoundRequest.MatchInfo matchInfo : request.matches()) {
            UUID matchId = UUID.fromString(matchInfo.matchId());
            UUID homeTeamId = UUID.fromString(matchInfo.homeTeamId());
            UUID awayTeamId = UUID.fromString(matchInfo.awayTeamId());

            matchIds.add(matchId);
            log.info("[ROUND-CONTROLLER] Processing match: {}", matchId);

            // V24D6M11: Build V24LiveSession if useV24DetailedEngine is enabled
            V24LiveSession v24LiveSession = buildV24LiveSession(career, matchId, homeTeamId, awayTeamId);

            if (v24LiveSession != null) {
                // V24 path — use MatchFinishedResult callback
                matchManagementService.startMatch(
                        userId,
                        matchId,
                        homeTeamId,
                        awayTeamId,
                        result -> handleMatchFinished(result, matchResults, matchesFinished, totalMatches, roundEngine, userId, career),
                        v24LiveSession)
                    .subscribe();
            } else {
                // Legacy path — use MatchStateSnapshot callback
                matchManagementService.startMatch(
                        userId,
                        matchId,
                        homeTeamId,
                        awayTeamId,
                        finalState -> {
                            matchResults.add(new MatchResultProcessor.MatchResultInfo(
                                    matchId.toString(),
                                    finalState.score().home(),
                                    finalState.score().away(),
                                    new ArrayList<>(finalState.events())
                            ));

                            int finished = matchesFinished.incrementAndGet();
                            log.info("[ROUND-CONTROLLER] Match {} finished, {}/{} total", matchId, finished, totalMatches);

                            if (finished == totalMatches) {
                                log.info("[ROUND-CONTROLLER] All matches finished, emitting completed state");
                                roundEngine.emitCompletedState();
                                orchestrator.processMatchDayResults(userId.toString(), matchResults)
                                        .subscribe();
                            }
                        })
                    .subscribe();
            }

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

    /**
     * V24D6M11: Build V24LiveSession from CareerSave if useV24DetailedEngine is enabled.
     * Returns null if V24 is disabled (falls back to legacy path).
     */
    private V24LiveSession buildV24LiveSession(CareerSave career, UUID matchId, UUID homeTeamId, UUID awayTeamId) {
        if (!useV24DetailedEngine) {
            log.debug("[ROUND-CONTROLLER] V24DetailedEngine disabled, using legacy path for match {}", matchId);
            return null;
        }

        try {
            String matchIdStr = matchId.toString();
            MatchFixture fixture = career.getTournamentState().getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(matchIdStr))
                    .findFirst()
                    .orElse(null);

            if (fixture == null) {
                log.warn("[ROUND-CONTROLLER] MatchFixture not found for match {}, using legacy path", matchId);
                return null;
            }

            String homeTeamIdStr = homeTeamId.toString();
            String awayTeamIdStr = awayTeamId.toString();

            var homeTeam = career.getSessionTeam(homeTeamIdStr);
            var awayTeam = career.getSessionTeam(awayTeamIdStr);
            if (homeTeam == null || awayTeam == null) {
                log.warn("[ROUND-CONTROLLER] SessionTeam not found for match {}, using legacy path", matchId);
                return null;
            }

            long seed = matchId.getLeastSignificantBits();
            V24MatchContext context = v24ContextFactory.build(career, fixture, homeTeam, awayTeam, seed);

            V24LiveSession session = new V24LiveSession(context, seed);
            log.info("[ROUND-CONTROLLER] V24LiveSession created for match {} with seed {}", matchId, seed);
            return session;
        } catch (Exception e) {
            log.error("[ROUND-CONTROLLER] Failed to create V24LiveSession for match {}, falling back to legacy: {}", matchId, e.getMessage());
            return null;
        }
    }

    /**
     * V24D6M11: Handle match finish from V24 path.
     * Uses v24Result.timeline().events() for persistence when V24LiveSession was active.
     * Also persists V24 detail data to Redis via LeagueSimulator.
     */
    private void handleMatchFinished(MatchFinishedResult result,
                                     java.util.List<MatchResultProcessor.MatchResultInfo> matchResults,
                                     AtomicInteger matchesFinished,
                                     int totalMatches,
                                     RoundEngine roundEngine,
                                     UUID userId,
                                     CareerSave career) {
        List<com.footballmanager.domain.model.entity.MatchEvent> events;
        if (result.v24Result() != null) {
            // V24 path: use timeline from V24DetailedMatchResult — convert V24MatchEvent to MatchEvent
            events = new java.util.ArrayList<>();
            for (var v24Event : result.v24Result().timeline().events()) {
                events.add(com.footballmanager.domain.model.entity.MatchEvent.of(
                        toDomainEventType(v24Event.type()),
                        v24Event.minute(),
                        v24Event.playerId(),
                        v24Event.playerName(),
                        v24Event.teamId(),
                        v24Event.description()
                ));
            }
            log.info("[ROUND-CONTROLLER] V24 match finished, {} timeline events for persistence", events.size());

            // V24D6M12: Persist V24 detail to Redis via LeagueSimulator
            // Pass the actual v24Result (with real timeline) instead of building an empty one
            leagueSimulator.persistV24DetailForLiveMatch(
                    career,
                    result.v24Result(),
                    result.snapshot().homeTeamId().toString(),
                    result.snapshot().awayTeamId().toString(),
                    result.snapshot().score().home(),
                    result.snapshot().score().away()
            );
        } else {
            events = new java.util.ArrayList<>(result.snapshot().events());
        }

        matchResults.add(new MatchResultProcessor.MatchResultInfo(
                result.snapshot().matchId().toString(),
                result.snapshot().score().home(),
                result.snapshot().score().away(),
                events
        ));

        int finished = matchesFinished.incrementAndGet();
        log.info("[ROUND-CONTROLLER] Match {} finished, {}/{} total", result.snapshot().matchId(), finished, totalMatches);

        if (finished == totalMatches) {
            log.info("[ROUND-CONTROLLER] All matches finished, emitting completed state");
            roundEngine.emitCompletedState();
            orchestrator.processMatchDayResults(userId.toString(), matchResults)
                    .subscribe();
        }
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

    /**
     * V24D6M11: Convert V24MatchEventType to domain MatchEvent.EventType.
     * Every V24MatchEventType maps explicitly — no lossy fallbacks.
     */
    private com.footballmanager.domain.model.entity.MatchEvent.EventType toDomainEventType(
            V24MatchEventType v24Type) {
        if (v24Type == null) {
            throw new IllegalArgumentException("V24MatchEventType cannot be null");
        }
        return switch (v24Type) {
            case GOAL -> com.footballmanager.domain.model.entity.MatchEvent.EventType.GOAL;
            case SHOT -> com.footballmanager.domain.model.entity.MatchEvent.EventType.SHOT;
            case SHOT_ON_TARGET -> com.footballmanager.domain.model.entity.MatchEvent.EventType.SHOT_ON_TARGET;
            case SAVE -> com.footballmanager.domain.model.entity.MatchEvent.EventType.SAVE;
            case MISS -> com.footballmanager.domain.model.entity.MatchEvent.EventType.MISS;
            case BLOCK -> com.footballmanager.domain.model.entity.MatchEvent.EventType.BLOCK;
            case CHANCE_CREATED -> com.footballmanager.domain.model.entity.MatchEvent.EventType.CHANCE_CREATED;
            case FOUL -> com.footballmanager.domain.model.entity.MatchEvent.EventType.FOUL;
            case YELLOW_CARD -> com.footballmanager.domain.model.entity.MatchEvent.EventType.YELLOW_CARD;
            case RED_CARD -> com.footballmanager.domain.model.entity.MatchEvent.EventType.RED_CARD;
            case INJURY -> com.footballmanager.domain.model.entity.MatchEvent.EventType.INJURY;
            case CORNER -> com.footballmanager.domain.model.entity.MatchEvent.EventType.CORNER;
            case OFFSIDE -> com.footballmanager.domain.model.entity.MatchEvent.EventType.OFFSIDE;
            case SUBSTITUTION -> com.footballmanager.domain.model.entity.MatchEvent.EventType.SUBSTITUTION;
        };
    }
}