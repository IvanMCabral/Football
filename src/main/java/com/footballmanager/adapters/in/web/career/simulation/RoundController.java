package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
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
import com.footballmanager.application.service.simulation.v24.BaselineState;
import com.footballmanager.application.service.simulation.v24.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.LiveRoundMutationTracking;
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
    // F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE): stores the pre-subs
    // BaselineState at match start, deletes it on match finish.
    private final BaselineStateStoragePort baselineStoragePort;
    // V24D12-B: use ControllerHelper for userId extraction; replaces the
    // copy-paste getUserIdFromAuth helper that accepted an optional
    // requestUserId from the body and threw IAE on auth failure.
    private final ControllerHelper controllerHelper;

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
        // LIVE-MATCH-F3-UI-LIVE F5.1 BUG-004: validate the body BEFORE
        // touching UUID.fromString. Without this guard, an empty / wrong-field
        // body (e.g. {gameId, round} from a misbehaving client) makes
        // `UUID.fromString(null)` throw a raw NPE
        // ("Cannot invoke String.length() because name is null") which
        // surfaces as HTTP 500. We map the missing/invalid roundId to a
        // 422 LINEUP_VALIDATION_ERROR with a clear message instead.
        if (request == null || request.roundId() == null || request.roundId().isBlank()) {
            return Mono.error(new IllegalArgumentException(
                "roundId is required and must be a non-blank UUID string"));
        }
        if (request.matches() == null || request.matches().isEmpty()) {
            return Mono.error(new IllegalArgumentException(
                "matches is required and must contain at least one match"));
        }
        UUID roundId = UUID.fromString(request.roundId());
        // V24D12-B: use the JWT identity. The legacy optional requestUserId
        // from the body is no longer honored - use the controller helper
        // for the 401 path consistency that V24D12 established.
        UUID userId = controllerHelper.getUserId(authentication);

        log.info("[ROUND-CONTROLLER] Starting round {} for user {}", roundId, userId);

        return startMatches(roundId, userId, request)
            .map(initialState -> ResponseEntity.ok(initialState))
            .onErrorResume(e -> {
                // V24D13-2 (FIX-001): protocol-level exceptions (IllegalStateException
                // from missing career/session, IllegalArgumentException from invalid
                // roundId UUID, MinuteInPastException from F2.5) must propagate to
                // GlobalExceptionHandler so the frontend gets the right 4xx semantic
                // code (422 LINEUP_STATE_ERROR, 422 LINEUP_VALIDATION_ERROR, 400
                // MINUTE_IN_PAST). Only genuinely unexpected errors (NPE, DB, etc.)
                // are mapped to 500 here.
                if (e instanceof IllegalStateException
                        || e instanceof IllegalArgumentException) {
                    return Mono.error(e);
                }
                log.error("[ROUND-CONTROLLER] Unexpected error starting round {}: {}", request.roundId(), e.getMessage(), e);
                return Mono.just(ResponseEntity.internalServerError().build());
            });
    }

    private Mono<RoundState> startMatches(UUID roundId, UUID userId, StartRoundRequest request) {
        RoundEngine roundEngine = new RoundEngine(roundId);
        log.info("[ROUND-CONTROLLER] Created RoundEngine for roundId: {}", roundId);

        final int totalMatches = request.matches().size();
        final AtomicInteger matchesFinished = new AtomicInteger(0);
        final List<MatchResultProcessor.MatchResultInfo> matchResults =
                Collections.synchronizedList(new ArrayList<>());

        // V24D13-2 (FIX-001): the previous implementation called
        // {@code careerSessionService.getCareerFromCache(userId).block()} here,
        // which throws {@code IllegalStateException("block() not supported in
        // thread parallel-N")} on Reactor parallel threads. The exception is
        // caught by {@code GlobalExceptionHandler} and surfaced to the
        // frontend as HTTP 422 LINEUP_STATE_ERROR, blocking the live smoke
        // (posesión animándose, sustitución en vivo). The fix loads the
        // CareerSave reactively via {@code flatMap}; the per-match side
        // effects (startMatch subscribe, engine registration, lifecycle
        // tracking) are dispatched inside {@code doOnNext} and run on the
        // Reactor scheduler, never blocking the caller thread.
        return careerSessionService.getCareerFromCache(userId)
            .switchIfEmpty(Mono.error(new IllegalStateException("Career not found for user: " + userId)))
            .doOnNext(career -> {
                log.info("[ROUND-CONTROLLER] CareerSave loaded for V24 context construction");
                // [V24D6M11-TRACE] Log careerId for E2E tracing
                String traceCareerId = career.getData().getCareerId();
                log.info("[V24D6M11-TRACE] RoundController careerId={}, roundId={}", traceCareerId, roundId);

                // V24D6R2: Initialize per-round tracking for lifecycle decrement
                int currentRound = career.getTournamentState().getCurrentRound();
                int currentSeason = career.getSeasonManager().getCurrentSeason();
                LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(currentRound, currentSeason);
                capturePreRoundState(career, tracking);

                // Iniciar todos los partidos
                for (StartRoundRequest.MatchInfo matchInfo : request.matches()) {
                    UUID matchId = UUID.fromString(matchInfo.matchId());
                    UUID homeTeamId = UUID.fromString(matchInfo.homeTeamId());
                    UUID awayTeamId = UUID.fromString(matchInfo.awayTeamId());

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
                                result -> handleMatchFinished(result, matchResults, matchesFinished, totalMatches, roundEngine, userId, career, tracking),
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
            })
            .flatMapMany(career -> Flux.fromIterable(request.matches()))
            .flatMap(matchInfo -> {
                UUID matchId = UUID.fromString(matchInfo.matchId());
                return matchManagementService.getMatchState(userId, matchId);
            })
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

            // F6 Sprint 2: capture the baseline state BEFORE any sub is
            // applied. The SubstitutionCommandUseCaseImpl hook will append
            // subs to this state as the manager makes changes. Cleaned up
            // in handleMatchFinished.
            //
            // V24D15-CLEANUP (BUG_COMPARE_404): storage port now returns
            // Mono<Void>. We subscribe on a bounded-elastic scheduler and
            // log a warn on failure — baseline persistence MUST NOT block
            // the live match start (the compare endpoint will simply 404
            // for that match if Redis is down).
            String careerId = career.getData().getCareerId();
            BaselineState baseline = BaselineState.empty(careerId, seed, context);
            baselineStoragePort.save(careerId, baseline)
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .doOnSuccess(v -> log.info(
                            "[F6-MATCH-COMPARE] BaselineState saved for matchId={}, careerId={}, seed={}",
                            matchId, careerId, seed))
                    .onErrorResume(e -> {
                        log.warn("[F6-MATCH-COMPARE] Failed to save baseline for matchId={}: {}",
                                matchId, e.getMessage());
                        return reactor.core.publisher.Mono.empty();
                    })
                    .subscribe();

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
                                     CareerSave career,
                                     LiveRoundMutationTracking tracking) {
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

            // V24D6R2: Persist V24 detail to Redis via LeagueSimulator with live tracking
            leagueSimulator.persistV24DetailForLiveMatch(
                    career,
                    result.v24Result(),
                    result.snapshot().homeTeamId().toString(),
                    result.snapshot().awayTeamId().toString(),
                    result.snapshot().score().home(),
                    result.snapshot().score().away(),
                    tracking
            );

            // F6 Sprint 2: clean up the BaselineState now that the live
            // detail is persisted. The compare endpoint will 404 for this
            // match (good — there's no "after the fact" state to compare
            // against). The TTL 7d would also expire it eventually, but
            // explicit cleanup is cleaner.
            //
            // V24D15-CLEANUP (BUG_COMPARE_404): storage port delete is now
            // Mono<Void>. Failure is non-fatal (TTL will reap it) but we
            // log warn so a sustained Redis outage is visible.
            String matchId = result.snapshot().matchId().toString();
            String careerId = career.getData().getCareerId();
            baselineStoragePort.delete(careerId, matchId)
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .doOnSuccess(v -> log.info(
                            "[F6-MATCH-COMPARE] BaselineState deleted for matchId={}, careerId={}",
                            matchId, careerId))
                    .onErrorResume(e -> {
                        log.warn("[F6-MATCH-COMPARE] Failed to delete baseline on match finish: {}",
                                e.getMessage());
                        return reactor.core.publisher.Mono.empty();
                    })
                    .subscribe();
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

            // V24D6R2: Apply end-of-round lifecycle decrements BEFORE orchestrator saves
            if (tracking != null) {
                leagueSimulator.applyEndOfRoundLiveLifecycle(
                        career,
                        tracking.roundNumber,
                        career.getTournamentState().getFixtures(),
                        tracking
                );
            }

            orchestrator.processMatchDayResults(userId.toString(), matchResults)
                    .subscribe();
        }
    }

    /**
     * V24D6R2: Capture pre-round state for suspended/injured players.
     * Mirrors {@code LeagueSimulator.capturePreRoundSuspendedPlayerIds} and
     * {@code capturePreRoundInjuredPlayerIds} but lives in the controller
     * since tracking is a per-round artifact created at startMatches.
     */
    private void capturePreRoundState(CareerSave career, LiveRoundMutationTracking tracking) {
        for (var team : career.getAllSessionTeams()) {
            for (String playerId : career.getSquadPlayerIds(team.getSessionTeamId())) {
                var player = career.getSessionPlayer(playerId);
                if (player == null) continue;
                if (Boolean.TRUE.equals(player.getSuspended())
                        && player.getSuspensionRemainingMatches() != null
                        && player.getSuspensionRemainingMatches() > 0) {
                    tracking.preRoundSuspendedPlayerIds.add(playerId);
                }
                if (Boolean.TRUE.equals(player.getInjured())
                        && player.getInjuryRemainingMatches() != null
                        && player.getInjuryRemainingMatches() > 0) {
                    tracking.preRoundInjuredPlayerIds.add(playerId);
                }
            }
        }
        log.info("[V24D6R2-LIVE-LIFECYCLE] Pre-round snapshot: suspended={}, injured={}, round={}, season={}",
                tracking.preRoundSuspendedPlayerIds.size(),
                tracking.preRoundInjuredPlayerIds.size(),
                tracking.roundNumber,
                tracking.seasonNumber);
    }

    public record StartRoundRequest(String roundId, String userId, List<MatchInfo> matches) {
        public record MatchInfo(String matchId, String homeTeamId, String awayTeamId) {}
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
            // LIVE-MATCH-F2-LIVE F5: tactical change maps 1:1.
            case TACTICAL_CHANGE -> com.footballmanager.domain.model.entity.MatchEvent.EventType.TACTICAL_CHANGE;
        };
    }
}