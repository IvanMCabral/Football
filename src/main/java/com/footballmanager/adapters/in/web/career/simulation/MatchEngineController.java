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
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
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
            // the F1 sink fix + F2 scheduler survival fix. Difference from
            // passing integration tests: a slow real consumer (Spring SSE
            // writer + Jackson + Netty chunked write + proxy buffer) on a
            // hot stream producer. Defensive fix:
            //   - .publishOn(boundedElastic()) â€” serialize downstream on
            //     a separate thread so the producer (round-engine scheduler)
            //     never blocks on TCP flush backpressure.
            //   - REMOVED .onBackpressureLatest(): it WAS dropping
            //     intermediate emits under any backpressure. The replay()
            //     sink from F1 already gives "latest wins" semantics for
            //     new subscribers + future emits are still delivered to
            //     all subscribers, so an additional onBackpressureLatest()
            //     was strictly harmful.
            //   - .doOnNext(...) debug log so runtime smoke can confirm
            //     emits are flowing through the controller layer.
            return roundEngine.getStateStream()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(rs -> log.debug("[SSE-STREAM] roundId={} emit: tick-minute={} status={} ({} matches)",
                        id,
                        rs.getMatches().isEmpty() ? -1 : rs.getMatches().get(0).currentMinute(),
                        rs.getStatus(),
                        rs.getMatches().size()))
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
    @PostMapping(value = "/{matchId}/pause", produces = "application/json;charset=UTF-8")
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
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request could not be processed"));
            });
    }

    /**
     * POST /api/v1/match-engine/{matchId}/resume
     * Resumes a paused match.
     */
    @PostMapping(value = "/{matchId}/resume", produces = "application/json;charset=UTF-8")
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
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request could not be processed"));
            });
    }

    /**
     * POST /api/v1/match-engine/{matchId}/stop
     * Stops a match.
     */
    @PostMapping(value = "/{matchId}/stop", produces = "application/json;charset=UTF-8")
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
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request could not be processed"));
            });
    }

    /**
     * GET /api/v1/match-engine/matches/{matchId}/roundId
     *
     * Resolves the roundId for a given matchId using
     * {@link RoundEngineRegistry#getRoundIdByMatchId(UUID)}.
     *
     * / formation modal from a {@code MatchState} (which carries
     * {@code matchId} but NOT {@code roundId}). To pause/resume the
     * round when the modal opens, the front-end needs to resolve
     * {@code roundId} from {@code matchId}. The
     * {@code MatchEngineService} caches the result on the client, so
     * this endpoint is hit only when the cache misses.
     *
     * <p>Returns 200 with {@code {matchId, roundId}} when the match is
     * registered, 404 when it is not (the round has been unregistered
     * after completion). 400 when the matchId is not a valid UUID.
     */
    @GetMapping(value = "/matches/{matchId}/roundId", produces = "application/json;charset=UTF-8")
    public Mono<ResponseEntity<Map<String, Object>>> getRoundIdForMatch(
            @PathVariable String matchId) {
        log.debug("[MATCH-CONTROLLER] getRoundIdForMatch called for matchId: {}", matchId);
        UUID matchIdUuid;
        try {
            matchIdUuid = UUID.fromString(matchId);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "error", "matchId is not a valid UUID",
                "matchId", matchId
            )));
        }

        return Mono.fromSupplier(() -> {
            UUID roundId = roundEngineRegistry.getRoundIdByMatchId(matchIdUuid);
            if (roundId == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "match is not registered in any active round",
                    "matchId", matchId
                ));
            }
            return ResponseEntity.ok(Map.of(
                "matchId", matchId,
                "roundId", roundId.toString()
            ));
        });
    }
}
