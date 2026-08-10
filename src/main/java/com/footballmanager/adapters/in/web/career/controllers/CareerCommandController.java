package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.dto.request.CareerStartRequest;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.career.SeasonAdvancementService;
import com.footballmanager.application.service.domain.GameService;
import com.footballmanager.application.observability.ResetTiming;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.port.in.career.AdvanceRoundUseCase;
import com.footballmanager.domain.port.in.career.ContinueSeasonUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CareerCommandController - Command endpoints para Career (escritura).
 *
 * Endpoints que modifican estado: POST, DELETE
 * Ruta base: /api/v1/career
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/career")
public class CareerCommandController {

    private final ControllerHelper controllerHelper;
    private final CareerSessionService sessionService;
    private final SeasonAdvancementService seasonAdvancementService;
    // registry is the single source of truth for live round engines; the
    // engine itself is `synchronized` + idempotent (RoundEngine.pauseAll
    // / resumeAll early-return if already in the requested state).
    private final RoundEngineRegistry roundEngineRegistry;
    // career-start flow also persists a Game entity sharing the career's
    // UUID. Without this, the dashboard's /games/{careerId} navigation
    // always 404s (Game entity never created).
    private final GameService gameService;

    public CareerCommandController(
            ControllerHelper controllerHelper,
            CareerSessionService sessionService,
            SeasonAdvancementService seasonAdvancementService,
            RoundEngineRegistry roundEngineRegistry,
            GameService gameService) {
        this.controllerHelper = controllerHelper;
        this.sessionService = sessionService;
        this.seasonAdvancementService = seasonAdvancementService;
        this.roundEngineRegistry = roundEngineRegistry;
        this.gameService = gameService;
    }

    /**
     * POST /api/v1/career/start
     * Inicia una nueva carrera para el usuario
     */
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> startCareer(
            @RequestBody CareerStartRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        Integer effectiveTeamsPerDivision = request.teamsPerDivision();

        final String leagueId = request.leagueId();
        final String difficulty = request.difficulty();
        final String gameSpeed = request.gameSpeed();

        return sessionService.startNewCareer(
                        userId,
                        request.leagueId(),
                        request.teamId(),
                        request.difficulty(),
                        request.gameSpeed(),
                        effectiveTeamsPerDivision
                )
                // careerCache right after a new CareerSave is persisted to Redis,
                // so subsequent /status reads do NOT return the previous career
                // (the cache would otherwise still hold the stale CareerSave from
                // the prior /career/start call). Mirror pattern used by
                // TestHarnessUseCaseImpl / ContinueSeasonUseCaseImpl / StartRoundUseCaseImpl.
                .doOnNext(started -> {
                    sessionService.invalidateCache(userId);
                    sessionService.cacheCareer(started);
                })
                // career is initialized, also persist a Game entity that
                // shares the career's UUID. Best-effort â€” if Redis fails,
                // we log warn and return success anyway so the live match
                // flow is never blocked.
                .flatMap(career -> gameService.createGameFromCareer(
                                career, leagueId, difficulty, gameSpeed, effectiveTeamsPerDivision)
                        .doOnError(err -> log.warn(
                                "Failed to persist Game entity "
                                        + "after career start for userId={}: {}",
                                userId, err.getMessage()))
                        .onErrorResume(err -> Mono.empty())
                        .then(Mono.just(career)))
                .then();
    }

    /**
     * DELETE /api/v1/career/reset
     * Elimina la carrera del usuario
     */
    @DeleteMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> resetCareer(Authentication authentication, ServerHttpResponse response) {
        UUID userId = controllerHelper.getUserId(authentication);
        long startedNanos = System.nanoTime();
        ResetTiming timing = new ResetTiming();
        AtomicLong careerNanos = new AtomicLong();
        AtomicLong gameNanos = new AtomicLong();
        response.beforeCommit(() -> {
            setResetTelemetry(response, timing, careerNanos, gameNanos, startedNanos);
            return Mono.empty();
        });
        long careerStarted = System.nanoTime();
        return sessionService.deleteCareer(userId, timing)
                .doFinally(signal -> careerNanos.set(System.nanoTime() - careerStarted))
                // Game entity that mirrors the career. Best-effort â€” we
                // already cleared the CareerSave; deleting the Game is
                // housekeeping so the dashboard does not show a phantom
                // game pointing to a deleted career.
                .then(Mono.defer(() -> {
                    // We don't know the careerId post-delete (it may have
                    // been wiped from the cache). For simplicity, look up
                    // the Game entity by userId only (the Game has the
                    // same UUID as the career that was just deleted â€” so
                    // findByUserId should return at most one entry).
                    long gameStarted = System.nanoTime();
                    return gameService.deleteAllGames(userId)
                            .doFinally(signal -> gameNanos.set(System.nanoTime() - gameStarted))
                            .then()
                            .doOnError(err -> log.warn(
                                    "Failed to delete Game entity "
                                            + "after career reset: type={}",
                                    err.getClass().getSimpleName()))
                            .onErrorResume(err -> Mono.empty());
                }))
                .then()
                .doOnSuccess(ignored -> {
                    setResetTelemetry(response, timing, careerNanos, gameNanos, startedNanos);
                });
    }

    private static void setResetTelemetry(ServerHttpResponse response,
                                          ResetTiming timing,
                                          AtomicLong careerNanos,
                                          AtomicLong gameNanos,
                                          long startedNanos) {
        response.getHeaders().set("X-Reset-Path", timing.diagnostic("path"));
        response.getHeaders().set("X-Reset-Manifest-Version", timing.diagnostic("manifestVersion"));
        response.getHeaders().set("X-Reset-Manifest-Entries", timing.diagnostic("manifestEntries"));
        response.getHeaders().set("X-Reset-Scan-Count", timing.diagnostic("scanCount"));
        response.getHeaders().set("X-Reset-Discovery-Ms", timing.diagnostic("discoveryMs"));
        response.getHeaders().set("X-Reset-Manifest-Read-Ms", timing.diagnostic("manifestReadMs"));
        response.getHeaders().set("X-Reset-Projection-Scan-Ms", timing.diagnostic("projectionScanMs"));
        response.getHeaders().set("X-Reset-Child-Unlink-Ms", timing.diagnostic("childUnlinkMs"));
        response.getHeaders().set("X-Reset-Metadata-Ms", timing.diagnostic("metadataMs"));
        response.getHeaders().set("X-Reset-Root-Unlink-Ms", timing.diagnostic("rootUnlinkMs"));
        response.getHeaders().set("X-Reset-Tombstone-Ms", timing.diagnostic("tombstoneMs"));
        response.getHeaders().set("X-Reset-Game-Cleanup-Ms", Long.toString(gameNanos.get() / 1_000_000L));
        response.getHeaders().set("X-Reset-Career-Ms", Long.toString(careerNanos.get() / 1_000_000L));
        response.getHeaders().set("X-Reset-Game-Ms", Long.toString(gameNanos.get() / 1_000_000L));
        response.getHeaders().set("X-Reset-Lookup-Ms", Long.toString(timing.careerLookupMs()));
        response.getHeaders().set("X-Reset-Registry-Ms", Long.toString(timing.registryMs()));
        response.getHeaders().set("X-Reset-Cleanup-Ms", Long.toString(timing.cleanupMs()));
        response.getHeaders().set("X-Reset-Server-Ms",
                Long.toString((System.nanoTime() - startedNanos) / 1_000_000L));
    }

    /**
     * POST /api/v1/career/{careerId}/next-round
     * Avanza a la siguiente fecha (solo si estado es WAITING_USER)
     * Retorna informaciÃ³n sobre el avance para el frontend.
     */
    @PostMapping("/{careerId}/next-round")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdvanceRoundUseCase.AdvanceResult> advanceToNextRound(
            @PathVariable String careerId,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        return seasonAdvancementService.advanceToNextRound(userId, careerId);
    }

    /**
     * POST /api/v1/career/continue
     * Inicia una nueva temporada (solo si torneo estÃ¡ en FINISHED)
     */
    @PostMapping("/continue")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ContinueSeasonUseCase.ContinueResult> continueToNewSeason(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return seasonAdvancementService.continueToNewSeason(userId);
    }

    /**
     * POST /api/v1/career/{careerId}/round/{roundId}/pause
     *
     * opens a substitution/formation modal, the modal wires a pause here
     * before {@code dialog.open(...)} so the {@code currentMinute} the
     * manager saw is still current when they confirm).
     *
     * <p>Delegates to {@link RoundEngine#pauseAll()} which is
     * {@code synchronized} and idempotent (early-returns if already paused
     * or the round is not running). The endpoint therefore inherits the
     * idempotency: calling it twice in a row yields the same observable
     * state, with {@code alreadyPaused=true} on the second call.
     *
     * <p>Returns 200 with the round state flags; 404 if the round is not
     * registered (no live engine for that roundId).
     */
    @PostMapping("/{careerId}/round/{roundId}/pause")
    public Mono<ResponseEntity<Map<String, Object>>> pauseRound(
            @PathVariable String careerId,
            @PathVariable String roundId,
            Authentication authentication) {
        // Extract userId â€” throws via ControllerHelper if JWT is missing/invalid.
        UUID userId = controllerHelper.getUserId(authentication);
        UUID roundIdUuid;
        try {
            roundIdUuid = UUID.fromString(roundId);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "roundId is not a valid UUID",
                "roundId", roundId
            )));
        }

        return Mono.fromSupplier(() -> {
            RoundEngine engine = roundEngineRegistry.get(roundIdUuid);
            if (engine == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", "round not found (no active engine for roundId)",
                    "careerId", careerId,
                    "roundId", roundId
                ));
            }

            // Capture state BEFORE pauseAll so we can report the transition.
            boolean wasPaused = engine.isPaused();
            boolean wasFinished = !engine.isRunning();

            // Idempotent: pauseAll early-returns if already paused.
            engine.pauseAll();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("careerId", careerId);
            body.put("roundId", roundId);
            body.put("alreadyPaused", wasPaused);
            body.put("alreadyFinished", wasFinished);
            body.put("userId", userId.toString());
            // The harness/live DT modal needs the exact match state after the
            // round is frozen (minute, score, possession, events, remaining
            // substitutions). Previously this endpoint returned only flags,
            // forcing the frontend to call the single-match state endpoint,
            // which is not available for round-engine matches and returned
            // 404. Keeping the flags preserves existing consumers while the
            // matches payload gives modals a real source of truth.
            body.put("matches", engine.getMatchStates());
            return ResponseEntity.ok(body);
        });
    }

    /**
     * POST /api/v1/career/{careerId}/round/{roundId}/resume
     *
     * Resumes ALL matches of the round. Wired by the modal
     * {@code afterClosed()} so the round re-runs as soon as the manager
     * confirms OR cancels the substitution/formation.
     *
     * <p>Same idempotency story as {@link #pauseRound}: delegates to
     * {@link RoundEngine#resumeAll()}, which is {@code synchronized} and
     * early-returns if not paused.
     */
    @PostMapping("/{careerId}/round/{roundId}/resume")
    public Mono<ResponseEntity<Map<String, Object>>> resumeRound(
            @PathVariable String careerId,
            @PathVariable String roundId,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        UUID roundIdUuid;
        try {
            roundIdUuid = UUID.fromString(roundId);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "roundId is not a valid UUID",
                "roundId", roundId
            )));
        }

        return Mono.fromSupplier(() -> {
            RoundEngine engine = roundEngineRegistry.get(roundIdUuid);
            if (engine == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", "round not found (no active engine for roundId)",
                    "careerId", careerId,
                    "roundId", roundId
                ));
            }

            boolean wasPaused = engine.isPaused();
            boolean wasFinished = !engine.isRunning();

            // Idempotent: resumeAll early-returns if not paused.
            engine.resumeAll();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("careerId", careerId);
            body.put("roundId", roundId);
            body.put("wasPaused", wasPaused);
            body.put("alreadyFinished", wasFinished);
            body.put("userId", userId.toString());
            body.put("matches", engine.getMatchStates());
            return ResponseEntity.ok(body);
        });
    }

}
