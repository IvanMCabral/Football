package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.MatchManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Controller for SSE streaming of round state.
 * Handles only real-time event streaming.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/match-engine")
@RequiredArgsConstructor
public class MatchEngineController {

    private final RoundEngineRegistry roundEngineRegistry;
    private final MatchManagementService matchManagementService;
    // V24D12-B: use ControllerHelper for userId extraction; replaces the
    // copy-paste getUserIdFromAuth helper that accepted an optional
    // requestUserId and threw IAE on auth failure.
    private final ControllerHelper controllerHelper;

    /**
     * GET /api/v1/match-engine/rounds/{roundId}/stream
     * SSE stream for round state updates.
     */
    @GetMapping(value = "/rounds/{roundId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<RoundState> streamRoundState(@PathVariable String roundId) {
        try {
            UUID id = UUID.fromString(roundId);

            RoundEngine roundEngine = roundEngineRegistry.get(id);
            if (roundEngine == null) {
                log.warn("[SSE-STREAM] Round engine not found for roundId: {}. Active engines: {}", id, roundEngineRegistry.getActiveRoundCount());
                return Flux.empty(); // Return empty flux instead of error
            }

            log.info("[SSE-STREAM] Streaming roundId: {}", id);
            return roundEngine.getStateStream()
                .doOnCancel(() -> log.info("[SSE-STREAM] Stream cancelled for roundId: {}", id))
                .doOnComplete(() -> log.info("[SSE-STREAM] Stream completed for roundId: {}", id));

        } catch (Exception e) {
            log.error("[SSE-STREAM] Error streaming roundId {}: {}", roundId, e.getMessage());
            return Flux.error(e);
        }
    }

    /**
     * POST /api/v1/match-engine/{matchId}/pause
     * Pauses a match directly by matchId.
     */
    @PostMapping("/{matchId}/pause")
    public Mono<ResponseEntity<Object>> pauseMatch(
            @PathVariable String matchId,
            Authentication authentication) {
        log.info("[MATCH-CONTROLLER] pauseMatch called for matchId: {}", matchId);
        UUID matchIdUuid = UUID.fromString(matchId);

        UUID userId = controllerHelper.getUserId(authentication);
        log.info("[MATCH-CONTROLLER] userId: {}", userId);

        return matchManagementService.pauseMatch(userId, matchIdUuid)
            .doOnSuccess(v -> log.info("[MATCH-CONTROLLER] Pause successful for matchId: {}", matchId))
            .doOnError(e -> log.error("[MATCH-CONTROLLER] Pause failed for matchId {}: {}", matchId, e.getMessage()))
            .then(Mono.just(ResponseEntity.ok().build()))
            .onErrorResume(e -> {
                log.error("[MATCH-CONTROLLER] Error in pauseMatch: {}", e.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()));
            });
    }

    /**
     * POST /api/v1/match-engine/{matchId}/resume
     * Resumes a paused match.
     */
    @PostMapping("/{matchId}/resume")
    public Mono<ResponseEntity<Object>> resumeMatch(
            @PathVariable String matchId,
            Authentication authentication) {
        log.info("[MATCH-CONTROLLER] resumeMatch called for matchId: {}", matchId);
        UUID matchIdUuid = UUID.fromString(matchId);

        UUID userId = controllerHelper.getUserId(authentication);
        log.info("[MATCH-CONTROLLER] userId: {}", userId);

        return matchManagementService.resumeMatch(userId, matchIdUuid)
            .doOnSuccess(v -> log.info("[MATCH-CONTROLLER] Resume successful for matchId: {}", matchId))
            .doOnError(e -> log.error("[MATCH-CONTROLLER] Resume failed for matchId {}: {}", matchId, e.getMessage()))
            .then(Mono.just(ResponseEntity.ok().build()))
            .onErrorResume(e -> {
                log.error("[MATCH-CONTROLLER] Error in resumeMatch: {}", e.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()));
            });
    }

    /**
     * POST /api/v1/match-engine/{matchId}/stop
     * Stops a match.
     */
    @PostMapping("/{matchId}/stop")
    public Mono<ResponseEntity<Object>> stopMatch(
            @PathVariable String matchId,
            Authentication authentication) {
        log.info("[MATCH-CONTROLLER] stopMatch called for matchId: {}", matchId);
        UUID matchIdUuid = UUID.fromString(matchId);

        UUID userId = controllerHelper.getUserId(authentication);
        log.info("[MATCH-CONTROLLER] userId: {}", userId);

        return matchManagementService.stopMatch(userId, matchIdUuid)
            .doOnSuccess(v -> log.info("[MATCH-CONTROLLER] Stop successful for matchId: {}", matchId))
            .doOnError(e -> log.error("[MATCH-CONTROLLER] Stop failed for matchId {}: {}", matchId, e.getMessage()))
            .then(Mono.just(ResponseEntity.ok().build()))
            .onErrorResume(e -> {
                log.error("[MATCH-CONTROLLER] Error in stopMatch: {}", e.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()));
            });
    }
}
