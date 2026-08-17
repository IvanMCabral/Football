package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.engine.model.RoundState;
import com.footballmanager.application.engine.round.RoundEngine;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.MatchManagementService;
import com.footballmanager.application.service.match.MatchSessionNotFoundException;
import com.footballmanager.application.service.security.RoundOwnershipAuthority;
import com.footballmanager.application.service.security.RoundOwnershipDeniedException;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
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
    private final RoundOwnershipAuthority roundOwnershipAuthority;

    /**
     * GET /api/v1/match-engine/rounds/{roundId}/stream
     * SSE stream for round state updates.
     */
    @GetMapping(value = "/rounds/{roundId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<RoundState> streamRoundState(@PathVariable String roundId,
                                             Authentication authentication) {
        final UUID id;
        try {
            id = UUID.fromString(roundId);
        } catch (IllegalArgumentException e) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "roundId is not a valid UUID"));
        }
        final UUID userId = controllerHelper.getUserId(authentication);
        return roundOwnershipAuthority.requireOwnedRound(userId, id)
                .flatMapMany(engine -> streamRoundStateInternal(roundId, id, engine))
                .onErrorMap(RoundOwnershipDeniedException.class,
                        ignored -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round stream is not available"));
    }

    /** Package-scoped compatibility entry point for isolated stream tests. */
    Flux<RoundState> streamRoundState(String roundId) {
        try {
            UUID id = UUID.fromString(roundId);
            RoundEngine roundEngine = roundEngineRegistry.get(id);
            return streamRoundStateInternal(roundId, id, roundEngine);
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    private Flux<RoundState> streamRoundStateInternal(String roundId, UUID id, RoundEngine roundEngine) {
        try {

            if (roundEngine == null) {
                log.warn("[SSE-STREAM] Round engine not found for roundId: {}. Active engines: {}", id, roundEngineRegistry.getActiveRoundCount());
                // A missing engine is terminal for this round. Returning an
                // empty 200 stream made clients reconnect forever after a
                // completed round had been unregistered.
                return Flux.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Round stream is no longer available"));
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
     * Returns the authoritative snapshot for a match while its round is live
     * or while the terminal snapshot is still retained by the round registry.
     * This endpoint is intentionally distinct from the legacy versus state
     * endpoint; the public live modal calls {@code /match-engine/{id}/state}.
     */
    @GetMapping(value = "/{matchId}/state", produces = "application/json;charset=UTF-8")
    public Mono<ResponseEntity<MatchStateSnapshot>> getMatchState(
            @PathVariable String matchId,
            Authentication authentication) {
        final UUID matchIdUuid;
        try {
            matchIdUuid = UUID.fromString(matchId);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        final UUID userId = controllerHelper.getUserId(authentication);
        return Mono.defer(() -> {
            RoundEngine roundEngine = roundEngineRegistry.getByMatchId(matchIdUuid);
            if (roundEngine != null) {
                if (!roundEngine.belongsTo(userId, null)) {
                    return Mono.just(ResponseEntity.notFound().build());
                }
                MatchStateSnapshot snapshot = roundEngine.getCurrentMatchSnapshot(matchIdUuid);
                if (snapshot == null) {
                    return Mono.just(ResponseEntity.notFound().<MatchStateSnapshot>build());
                }
                return Mono.just(authorizedSnapshot(userId, snapshot));
            }
            // The round registry is intentionally in-memory and is cleared
            // after final persistence. The session registry remains the
            // short-lived source for a just-completed match, so a refresh
            // must read that state instead of fabricating 0-0 or returning a
            // misleading success response.
            return matchManagementService.getMatchState(userId, matchIdUuid)
                .map(snapshot -> authorizedSnapshot(userId, snapshot))
                .defaultIfEmpty(ResponseEntity.notFound().build());
        });
    }

    private ResponseEntity<MatchStateSnapshot> authorizedSnapshot(UUID userId, MatchStateSnapshot snapshot) {
        if (snapshot.userId() == null || !snapshot.userId().equals(userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(snapshot);
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
            .onErrorResume(MatchSessionNotFoundException.class,
                    e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
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
            .onErrorResume(MatchSessionNotFoundException.class,
                    e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
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
            .onErrorResume(MatchSessionNotFoundException.class,
                    e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
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
            @PathVariable String matchId,
            Authentication authentication) {
        return getRoundIdForMatchProtected(matchId, authentication);
    }

    /** Package-scoped compatibility entry point for isolated lookup tests. */
    Mono<ResponseEntity<Map<String, Object>>> getRoundIdForMatch(String matchId) {
        return getRoundIdForMatchUnprotected(matchId);
    }

    private Mono<ResponseEntity<Map<String, Object>>> getRoundIdForMatchProtected(
            String matchId, Authentication authentication) {
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

        UUID userId = controllerHelper.getUserId(authentication);
        return roundOwnershipAuthority.requireOwnedRoundIdForMatch(userId, matchIdUuid)
            .map(roundId -> ResponseEntity.<Map<String, Object>>ok(Map.of(
                "matchId", matchId,
                "roundId", roundId.toString()
            )))
            .onErrorResume(RoundOwnershipDeniedException.class, ignored ->
                Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).<Map<String, Object>>body(Map.of(
                    "error", "match is not registered in an owned active round",
                    "matchId", matchId
                ))));
    }

    private Mono<ResponseEntity<Map<String, Object>>> getRoundIdForMatchUnprotected(String matchId) {
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
