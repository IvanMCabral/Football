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
import com.footballmanager.application.service.reactive.ReactiveLifecycleExecutor;
import com.footballmanager.application.service.simulation.detailed.BaselineState;
import com.footballmanager.application.service.simulation.detailed.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.application.service.simulation.detailed.LiveRoundMutationTracking;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Match;
import com.footballmanager.domain.model.entity.MatchFinishedResult;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.MatchId;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.ports.out.match.MatchRepository;
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
    private final MatchContextFactory matchContextFactory;
    private final LeagueSimulator leagueSimulator;
    private final MatchRepository matchRepository;
    private final BaselineStateStoragePort baselineStoragePort;
    private final ControllerHelper controllerHelper;
    private final ReactiveLifecycleExecutor lifecycleExecutor;

    @Value("${simulation.use-detailed-match-engine:true}")
    private boolean useDetailedMatchEngine;

    @PostMapping(value = "/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/json;charset=UTF-8")
    public Mono<ResponseEntity<RoundState>> startRound(@RequestBody StartRoundRequest request, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        if (request == null || request.roundId() == null || request.roundId().isBlank()) {
            return Mono.error(new IllegalArgumentException(
                "roundId is required and must be a non-blank UUID string"));
        }
        UUID roundId = UUID.fromString(request.roundId());

        log.info("[ROUND-CONTROLLER] Starting round {} for user {}", roundId, userId);

        return startMatches(roundId, userId, request)
            .map(initialState -> ResponseEntity.ok(initialState))
            .onErrorResume(e -> {
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
        return careerSessionService.getCareerFromCache(userId)
            .switchIfEmpty(Mono.error(new IllegalStateException("Career not found for user: " + userId)))
            .flatMapMany(career -> {
                log.info("[ROUND-CONTROLLER] CareerSave loaded for detailed match context construction");
                String traceCareerId = career.getData().getCareerId();
                log.info("RoundController careerId={}, roundId={}", traceCareerId, roundId);

                int currentRound = career.getTournamentState().getCurrentRound();
                int currentSeason = career.getSeasonManager().getCurrentSeason();
                LiveRoundMutationTracking tracking = new LiveRoundMutationTracking(currentRound, currentSeason);
                capturePreRoundState(career, tracking);
                List<Mono<Void>> matchStarts = new ArrayList<>();
                for (StartRoundRequest.MatchInfo matchInfo : request.matches()) {
                    UUID matchId = UUID.fromString(matchInfo.matchId());
                    UUID homeTeamId = UUID.fromString(matchInfo.homeTeamId());
                    UUID awayTeamId = UUID.fromString(matchInfo.awayTeamId());

                    log.info("[ROUND-CONTROLLER] Processing match: {}", matchId);

                    LiveSession detailedMatchSession = buildLiveSession(career, matchId, homeTeamId, awayTeamId);

                    if (detailedMatchSession != null) {
                        matchStarts.add(matchManagementService.startMatch(
                                userId,
                                matchId,
                                homeTeamId,
                                awayTeamId,
                                result -> handleMatchFinished(result, matchResults, matchesFinished, totalMatches, roundEngine, userId, career, tracking),
                                detailedMatchSession)
                            .take(1)
                            .then());
                    } else {
                        matchStarts.add(matchManagementService.startMatch(
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
                                        lifecycleExecutor.execute(
                                                "process legacy match-day results",
                                                orchestrator.processMatchDayResults(userId.toString(), matchResults));
                                    }
                                })
                            .take(1)
                            .then());
                    }

                    MatchEngine matchEngine = engineRegistry.startEngine(userId, matchId, homeTeamId, awayTeamId);
                    log.info("[ROUND-CONTROLLER] Got MatchEngine for match {}: {}", matchId, matchEngine != null ? "OK" : "NULL");
                    roundEngine.registerMatch(matchId, matchEngine);
                }

                roundEngineRegistry.register(roundId, roundEngine);
                log.info("[ROUND-CONTROLLER] Registered round engine, calling start()");
                return Mono.whenDelayError(matchStarts)
                        .then(Mono.fromRunnable(() -> {
                            roundEngine.start();
                            log.info("[ROUND-CONTROLLER] Round engine start() called, isRunning: {}",
                                    roundEngine.isRunning());
                        }))
                        .thenMany(Flux.fromIterable(request.matches())
                                .flatMap(matchInfo -> {
                                    UUID matchId = UUID.fromString(matchInfo.matchId());
                                    return matchManagementService.getMatchState(userId, matchId);
                                }));
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

    private LiveSession buildLiveSession(CareerSave career, UUID matchId, UUID homeTeamId, UUID awayTeamId) {
        if (!useDetailedMatchEngine) {
            log.debug("[ROUND-CONTROLLER] DetailedMatchEngine disabled, using legacy path for match {}", matchId);
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
            MatchContext context = matchContextFactory.build(career, fixture, homeTeam, awayTeam, seed);

            LiveSession session = new LiveSession(context, seed);
            log.info("[ROUND-CONTROLLER] LiveSession created for match {} with seed {}", matchId, seed);
            String careerId = career.getData().getCareerId();
            BaselineState baseline = BaselineState.empty(careerId, seed, context);
            lifecycleExecutor.execute("save baseline state",
                    baselineStoragePort.save(careerId, baseline)
                    .doOnSuccess(v -> log.info(
                            "[F6-MATCH-COMPARE] BaselineState saved for matchId={}, careerId={}, seed={}",
                            matchId, careerId, seed))
                    .onErrorResume(e -> {
                        log.warn("[F6-MATCH-COMPARE] Failed to save baseline for matchId={}: {}",
                                matchId, e.getMessage());
                        return reactor.core.publisher.Mono.empty();
                    }));

            return session;
        } catch (Exception e) {
            log.error("[ROUND-CONTROLLER] Failed to create LiveSession for match {}, falling back to legacy: {}", matchId, e.getMessage());
            return null;
        }
    }

    private void handleMatchFinished(MatchFinishedResult result,
                                     java.util.List<MatchResultProcessor.MatchResultInfo> matchResults,
                                     AtomicInteger matchesFinished,
                                     int totalMatches,
                                     RoundEngine roundEngine,
                                     UUID userId,
                                     CareerSave career,
                                     LiveRoundMutationTracking tracking) {
        List<com.footballmanager.domain.model.entity.MatchEvent> events;
        if (result.detailedResult() instanceof DetailedMatchResult detailedResult) {
            events = new java.util.ArrayList<>();
            for (var v24Event : detailedResult.timeline().events()) {
                events.add(com.footballmanager.domain.model.entity.MatchEvent.of(
                        toDomainEventType(v24Event.type()),
                        v24Event.minute(),
                        v24Event.playerId(),
                        v24Event.playerName(),
                        v24Event.teamId(),
                        v24Event.description()
                ));
            }
            log.info("[ROUND-CONTROLLER] detailed match finished, {} timeline events for persistence", events.size());
            log.info("[DETAIL-CALLSITE-PERSIST] careerId={}, matchId={}, "
                    + "homeGoals={}, awayGoals={}, homeTeamId={}, awayTeamId={}",
                career.getData().getCareerId(),
                result.snapshot().matchId(),
                result.snapshot().score().home(),
                result.snapshot().score().away(),
                result.snapshot().homeTeamId(),
                result.snapshot().awayTeamId());
            lifecycleExecutor.execute("persist detailed live detail",
                leagueSimulator.persistDetailedMatchDetailForLiveMatch(
                    career,
                    detailedResult,
                    result.snapshot().homeTeamId().toString(),
                    result.snapshot().awayTeamId().toString(),
                    result.snapshot().score().home(),
                    result.snapshot().score().away(),
                    tracking
                ));
            log.info("[F6-MATCH-COMPARE] BaselineState PRESERVED for matchId={}, careerId={} (TTL 7d, compare endpoint will use it)",
                    result.snapshot().matchId(), career.getData().getCareerId());
        } else {
            events = new java.util.ArrayList<>(result.snapshot().events());
        }

        matchResults.add(new MatchResultProcessor.MatchResultInfo(
                result.snapshot().matchId().toString(),
                result.snapshot().score().home(),
                result.snapshot().score().away(),
                events
        ));
        lifecycleExecutor.execute("persist finished match",
                persistFinishedMatch(result, events, career, userId));

        int finished = matchesFinished.incrementAndGet();
        log.info("[ROUND-CONTROLLER] Match {} finished, {}/{} total", result.snapshot().matchId(), finished, totalMatches);

        if (finished == totalMatches) {
            log.info("[ROUND-CONTROLLER] All matches finished, emitting completed state");
            roundEngine.emitCompletedState();

            if (tracking != null) {
                leagueSimulator.applyEndOfRoundLiveLifecycle(
                        career,
                        tracking.roundNumber,
                        career.getTournamentState().getFixtures(),
                        tracking
                );
            }

            lifecycleExecutor.execute(
                    "process detailed match-day results",
                    orchestrator.processMatchDayResults(userId.toString(), matchResults));
        }
    }

    private Mono<Void> persistFinishedMatch(MatchFinishedResult result,
                                      java.util.List<com.footballmanager.domain.model.entity.MatchEvent> events,
                                      CareerSave career,
                                      UUID authUserId) {
        try {
            MatchStateSnapshot snap = result.snapshot();
            if (snap.matchId() == null || snap.homeTeamId() == null || snap.awayTeamId() == null) {
                log.warn("[C41] persistFinishedMatch skipped - incomplete snapshot (matchId={}, home={}, away={})",
                    snap.matchId(), snap.homeTeamId(), snap.awayTeamId());
                return Mono.empty();
            }
            int homeGoals = snap.score() != null ? snap.score().home() : 0;
            int awayGoals = snap.score() != null ? snap.score().away() : 0;
            MatchResult matchResult = MatchResult.of(
                homeGoals, awayGoals,
                50, 50,
                homeGoals * 3, awayGoals * 3,
                events,
                null
            );
            int round = 1;
            java.time.Instant scheduledAt = java.time.Instant.now();
            if (career != null && career.getTournamentState() != null
                && career.getTournamentState().getFixtures() != null) {
                var fixture = career.getTournamentState().getFixtures().stream()
                    .filter(f -> snap.matchId().toString().equals(f.getMatchId()))
                    .findFirst()
                    .orElse(null);
                if (fixture != null) {
                    round = fixture.getRound();
                }
            }
            Match match = Match.schedule(
                MatchId.of(snap.matchId()),
                TeamId.of(snap.homeTeamId()),
                TeamId.of(snap.awayTeamId()),
                scheduledAt,
                round
            );
            match.simulate(matchResult);
            UUID userId = authUserId;
            if (userId == null) {
                String snapUserId = snap.userId();
                if (snapUserId != null && !snapUserId.isBlank()) {
                    try { userId = UUID.fromString(snapUserId); } catch (Exception ignored) {}
                }
                if (userId == null && career != null) {
                    userId = career.getUserId();
                }
            }
            if (userId == null) {
                log.error("[C41] persistFinishedMatch ABORTED - cannot determine userId for matchId={}. "
                    + "snap.userId={}, career.userId={}, authUserId={}. "
                    + "Match will NOT be persisted (avoids orphan keys that /api/v1/matches cannot find).",
                    snap.matchId(), snap.userId(),
                    career != null ? career.getUserId() : null,
                    authUserId);
                return Mono.empty();
            }
            final UUID persistUserId = userId;
            return matchRepository.save(persistUserId, match)
                .doOnSuccess(v -> log.info("[C41] Persisted finished match matchId={} to MatchRepository (userId={})",
                    snap.matchId(), persistUserId))
                .doOnError(err -> log.warn("[C41] Failed to persist finished match matchId={}: {}",
                    snap.matchId(), err.getMessage()))
                .onErrorResume(err -> reactor.core.publisher.Mono.empty());
        } catch (Exception e) {
            log.warn("[C41] persistFinishedMatch threw for matchId={}: {}",
                result.snapshot().matchId(), e.getMessage());
            return Mono.empty();
        }
    }

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
        log.info("Pre-round snapshot: suspended={}, injured={}, round={}, season={}",
                tracking.preRoundSuspendedPlayerIds.size(),
                tracking.preRoundInjuredPlayerIds.size(),
                tracking.roundNumber,
                tracking.seasonNumber);
    }

    public record StartRoundRequest(String roundId, String userId, List<MatchInfo> matches) {
        public record MatchInfo(String matchId, String homeTeamId, String awayTeamId) {}
    }

    private com.footballmanager.domain.model.entity.MatchEvent.EventType toDomainEventType(
            DetailedMatchEventType v24Type) {
        if (v24Type == null) {
            throw new IllegalArgumentException("DetailedMatchEventType cannot be null");
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
            case TACTICAL_CHANGE -> com.footballmanager.domain.model.entity.MatchEvent.EventType.TACTICAL_CHANGE;
        };
    }
}
