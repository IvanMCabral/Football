package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.dto.request.CareerStartRequest;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.career.SeasonAdvancementService;
import com.footballmanager.application.service.domain.GameService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.port.in.career.AdvanceRoundUseCase;
import com.footballmanager.domain.port.in.career.ContinueSeasonUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CareerCommandController - Command endpoints para Career (escritura).
 *
 * Endpoints que modifican estado: POST, DELETE
 * Ruta base: /api/v1/career
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/career")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CareerCommandController {

    private final ControllerHelper controllerHelper;
    private final CareerSessionService sessionService;
    private final SeasonAdvancementService seasonAdvancementService;
    // LIVE-MATCH-F5.3.2 BUG-015: per-round pause/resume endpoints. The
    // registry is the single source of truth for live round engines; the
    // engine itself is `synchronized` + idempotent (RoundEngine.pauseAll
    // / resumeAll early-return if already in the requested state).
    private final RoundEngineRegistry roundEngineRegistry;
    // V24D15-CLEANUP (BUG_GAME_DASHBOARD_404): wire GameService so the
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
        // teamsPerDivision es requerido — default a 5 si viene null (antiguo frontend)
        int effectiveTeamsPerDivision = request.teamsPerDivision() != null
                ? request.teamsPerDivision()
                : 5;

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
                // V24D15-CLEANUP (BUG_GAME_DASHBOARD_404): after the
                // career is initialized, also persist a Game entity that
                // shares the career's UUID. Best-effort — if Redis fails,
                // we log warn and return success anyway so the live match
                // flow is never blocked.
                .flatMap(career -> gameService.createGameFromCareer(
                                career, leagueId, difficulty, gameSpeed, effectiveTeamsPerDivision)
                        .doOnError(err -> log.warn(
                                "[V24D15-CLEANUP] Failed to persist Game entity "
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
    public Mono<Void> resetCareer(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return sessionService.deleteCareer(userId)
                // V24D15-CLEANUP (BUG_GAME_DASHBOARD_404): also delete the
                // Game entity that mirrors the career. Best-effort — we
                // already cleared the CareerSave; deleting the Game is
                // housekeeping so the dashboard does not show a phantom
                // game pointing to a deleted career.
                .then(Mono.defer(() -> {
                    // We don't know the careerId post-delete (it may have
                    // been wiped from the cache). For simplicity, look up
                    // the Game entity by userId only (the Game has the
                    // same UUID as the career that was just deleted — so
                    // findByUserId should return at most one entry).
                    return gameService.getAllGames(userId)
                            .collectList()
                            .flatMap(games -> {
                                if (games.isEmpty()) {
                                    return Mono.empty();
                                }
                                // Delete each Game entity (typically one).
                                return reactor.core.publisher.Flux.fromIterable(games)
                                        .flatMap(game -> gameService.deleteGame(
                                                userId,
                                                com.footballmanager.domain.model.valueobject.GameId.of(
                                                        game.getId().getValue())))
                                        .then();
                            })
                            .doOnError(err -> log.warn(
                                    "[V24D15-CLEANUP] Failed to delete Game entity "
                                            + "after career reset for userId={}: {}",
                                    userId, err.getMessage()))
                            .onErrorResume(err -> Mono.empty());
                }))
                .then();
    }

    /**
     * POST /api/v1/career/{careerId}/next-round
     * Avanza a la siguiente fecha (solo si estado es WAITING_USER)
     * Retorna información sobre el avance para el frontend.
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
     * Inicia una nueva temporada (solo si torneo está en FINISHED)
     */
    @PostMapping("/continue")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ContinueSeasonUseCase.ContinueResult> continueToNewSeason(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return seasonAdvancementService.continueToNewSeason(userId);
    }

    // ========== LIVE-MATCH-F5.3.2 BUG-015: per-round pause / resume ==========

    /**
     * POST /api/v1/career/{careerId}/round/{roundId}/pause
     *
     * Pauses ALL matches of the round (live BUG-015: when the manager
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
        // Extract userId — throws via ControllerHelper if JWT is missing/invalid.
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
            return ResponseEntity.ok(body);
        });
    }

}
