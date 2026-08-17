package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.application.service.simulation.detailed.MatchComparison;
import com.footballmanager.application.service.simulation.detailed.MatchComparisonService;
import com.footballmanager.application.service.simulation.detailed.MatchComparisonService.BaselineNotFoundException;
import com.footballmanager.application.service.simulation.detailed.MatchComparisonService.LiveDetailNotFoundException;
import com.footballmanager.application.service.simulation.detailed.TimelineSnapshotBuilder;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchQueryService;
import com.footballmanager.application.service.simulation.detailed.TimelineSnapshot;
import com.footballmanager.application.service.security.CareerOwnershipAuthority;
import com.footballmanager.application.service.security.CareerOwnershipDeniedException;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Supplier;

/**
 *
 * <p>GET /api/v1/careers/{careerId}/matches/{matchId}/detail
 *
 * <p>Feature-gated: returns 404 when {@code app.simulation.detailed.expose-detail-api=false}.
 * Does NOT enable detailed match simulation, persistence, or any production simulation path.
 *
 * career namespace and the dev proxy (/api/v1 -> localhost:8080). Previously
 * the controller sat at /api/careers (no v1) which meant frontend calls
 * landed on a 404 through the proxy.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/careers")
public class DetailedMatchController {

    private final DetailedMatchQueryService queryService;
    private final MatchComparisonService matchComparisonService;
    private final CareerOwnershipAuthority ownershipAuthority;
    private final ControllerHelper controllerHelper;

    public DetailedMatchController(
            DetailedMatchQueryService queryService,
            MatchComparisonService matchComparisonService) {
        this(queryService, matchComparisonService, null, null);
    }

    @Autowired
    public DetailedMatchController(
            DetailedMatchQueryService queryService,
            MatchComparisonService matchComparisonService,
            CareerOwnershipAuthority ownershipAuthority,
            ControllerHelper controllerHelper) {
        this.queryService = queryService;
        this.matchComparisonService = matchComparisonService;
        this.ownershipAuthority = ownershipAuthority;
        this.controllerHelper = controllerHelper;
    }

    /**
     * GET /api/v1/careers/{careerId}/matches/{matchId}/detail
     *
     * - Feature flag expose-detail-api is true
     * - Detail exists in Redis for the given careerId + matchId
     *
     * Returns 404 if:
     * - Feature flag is disabled (API hidden)
     * - Feature flag is enabled but no detail stored for this matchId
     *
     * Returns 400 if careerId or matchId is blank.
     */
    @GetMapping("/{careerId}/matches/{matchId}/detail")
    public Mono<ResponseEntity<Object>> getDetail(
            @PathVariable String careerId,
            @PathVariable String matchId,
            Authentication authentication) {
        return protectedRead(careerId, authentication, () -> getDetailInternal(careerId, matchId));
    }

    /** Compatibility entry point for isolated controller tests. */
    public Mono<ResponseEntity<Object>> getDetail(String careerId, String matchId) {
        return getDetailInternal(careerId, matchId);
    }

    private Mono<ResponseEntity<Object>> getDetailInternal(String careerId, String matchId) {

        if (careerId == null || careerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "careerId must not be blank")));
        }
        if (matchId == null || matchId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "matchId must not be blank")));
        }

        if (!queryService.isApiEnabled()) {
            log.debug("Detail API disabled, returning 404 for careerId={}, matchId={}", careerId, matchId);
            return Mono.just(ResponseEntity.notFound().build());
        }

        return queryService.findDetail(careerId, matchId)
                .flatMap(optionalDetail -> {
                    if (optionalDetail.isPresent()) {
                        return Mono.just(ResponseEntity.ok(optionalDetail.get()));
                    } else {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                });
    }

    /**
     * GET /api/v1/careers/{careerId}/matches/{matchId}/compare
     *
     * <p>Returns a {@link MatchComparison} with the baseline (what would
     * have happened with no manager interventions), the live result, and
     * the diff. See {@link MatchComparisonService#getComparison} for the
     * algorithm.
     *
     * <p>Reuses the same feature flag as {@code /detail}
     * ({@code app.simulation.detailed.expose-detail-api}).
     *
     * <p>Returns 400 if careerId or matchId is blank, 404 if:
     * <ul>
     *   <li>Feature flag is disabled.</li>
     *   <li>No live detail for the match (not finished yet, or detailed match path
     *       was disabled for the career).</li>
     *   <li>No baseline state for the match (already cleaned up, or TTL
     *       7d expired).</li>
     * </ul>
     */
    @GetMapping("/{careerId}/matches/{matchId}/compare")
    public Mono<ResponseEntity<Object>> getCompare(
            @PathVariable String careerId,
            @PathVariable String matchId,
            Authentication authentication) {
        return protectedRead(careerId, authentication, () -> getCompareInternal(careerId, matchId));
    }

    /** Compatibility entry point for isolated controller tests. */
    public Mono<ResponseEntity<Object>> getCompare(String careerId, String matchId) {
        return getCompareInternal(careerId, matchId);
    }

    private Mono<ResponseEntity<Object>> getCompareInternal(String careerId, String matchId) {

        if (careerId == null || careerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "careerId must not be blank")));
        }
        if (matchId == null || matchId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "matchId must not be blank")));
        }

        if (!queryService.isApiEnabled()) {
            log.debug("[F6-MATCH-COMPARE] Compare API disabled, returning 404 for careerId={}, matchId={}",
                    careerId, matchId);
            return Mono.just(ResponseEntity.notFound().build());
        }

        // Mono<MatchComparison> so it composes correctly with the Reactor
        // scheduler (the sync version was silently aborting under Reactor
        // parallel scheduling Ã¢â‚¬â€ blockOptional() threw IllegalStateException
        // which was caught and turned into Optional.empty, making the
        // endpoint return 404 even when the baseline was in Redis).
        return matchComparisonService.getComparison(careerId, matchId)
                .map(cmp -> ResponseEntity.ok((Object) cmp))
                .onErrorResume(BaselineNotFoundException.class, e -> {
                    log.info("[F6-MATCH-COMPARE] Baseline not found for careerId={}, matchId={}",
                            careerId, matchId);
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(LiveDetailNotFoundException.class, e -> {
                    log.info("[F6-MATCH-COMPARE] Live detail not found for careerId={}, matchId={}",
                            careerId, matchId);
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    log.warn("[F6-MATCH-COMPARE] Invalid argument for careerId={}, matchId={}: {}",
                            careerId, matchId, e.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", "Detailed match unavailable")));
                });
    }

    /**
     *
     * <p>Returns a partial snapshot of the stored match data filtered up to
     * and including minute N. Used by the test-harness UI timeline scrubber.
     * Pure derivation from the stored timeline Ã¢â‚¬â€ no re-simulation, no cache.
     *
     * <p>Feature-gated: returns 404 when
     * {@code app.simulation.detailed.expose-detail-api=false} (same flag as
     * {@code /detail} and {@code /compare}).
     *
     * <p>Returns 400 if:
     * <ul>
     *   <li>{@code careerId} or {@code matchId} is blank.</li>
     *   <li>{@code minute} is null, negative, or &gt; 130 (engine range).</li>
     * </ul>
     *
     * <p>Returns 404 if:
     * <ul>
     *   <li>Feature flag is disabled.</li>
     *   <li>No detail stored for the given matchId.</li>
     * </ul>
     */
    @GetMapping("/{careerId}/matches/{matchId}/timeline")
    public Mono<ResponseEntity<Object>> getTimeline(
            @PathVariable String careerId,
            @PathVariable String matchId,
            @RequestParam(name = "minute", required = true) Integer minute,
            Authentication authentication) {
        return protectedRead(careerId, authentication, () -> getTimelineInternal(careerId, matchId, minute));
    }

    /** Compatibility entry point for isolated controller tests. */
    public Mono<ResponseEntity<Object>> getTimeline(String careerId, String matchId, Integer minute) {
        return getTimelineInternal(careerId, matchId, minute);
    }

    private Mono<ResponseEntity<Object>> getTimelineInternal(String careerId, String matchId, Integer minute) {

        if (careerId == null || careerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "careerId must not be blank")));
        }
        if (matchId == null || matchId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "matchId must not be blank")));
        }
        if (minute == null || minute < 0 || minute > 130) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "minute must be between 0 and 130")));
        }

        if (!queryService.isApiEnabled()) {
            log.debug("Timeline API disabled, returning 404 for careerId={}, matchId={}",
                    careerId, matchId);
            return Mono.just(ResponseEntity.notFound().build());
        }

        return queryService.findDetail(careerId, matchId)
                .map(optionalDetail -> optionalDetail
                        .<ResponseEntity<Object>>map(detail ->
                                ResponseEntity.ok((Object) TimelineSnapshotBuilder.build(detail, minute)))
                         .orElseGet(() -> ResponseEntity.notFound().build()));
    }

    private Mono<ResponseEntity<Object>> protectedRead(
            String careerId,
            Authentication authentication,
            Supplier<Mono<ResponseEntity<Object>>> read) {
        Mono<Void> authorization = authentication == null || ownershipAuthority == null
                ? Mono.empty()
                : ownershipAuthority.requireOwned(controllerHelper.getUserId(authentication), careerId).then();
        return authorization.then(Mono.defer(read))
                .onErrorResume(CareerOwnershipDeniedException.class,
                        ignored -> Mono.just(ResponseEntity.notFound().build()));
    }
}
