package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.application.service.simulation.v24.MatchComparison;
import com.footballmanager.application.service.simulation.v24.MatchComparisonService;
import com.footballmanager.application.service.simulation.v24.MatchComparisonService.BaselineNotFoundException;
import com.footballmanager.application.service.simulation.v24.MatchComparisonService.LiveDetailNotFoundException;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * V24D4C: Controller for querying V24 detailed match data.
 *
 * <p>GET /api/v1/careers/{careerId}/matches/{matchId}/detail
 *
 * <p>Feature-gated: returns 404 when {@code app.simulation.v24.expose-detail-api=false}.
 * Does NOT enable V24 simulation, persistence, or any production simulation path.
 *
 * <p>V24D6O: Path moved to /api/v1/careers to align with the rest of the
 * career namespace and the dev proxy (/api/v1 -> localhost:8080). Previously
 * the controller sat at /api/careers (no v1) which meant frontend calls
 * landed on a 404 through the proxy.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/careers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class V24DetailedMatchController {

    private final V24DetailedMatchQueryService queryService;
    private final MatchComparisonService matchComparisonService;

    public V24DetailedMatchController(
            V24DetailedMatchQueryService queryService,
            MatchComparisonService matchComparisonService) {
        this.queryService = queryService;
        this.matchComparisonService = matchComparisonService;
    }

    /**
     * GET /api/v1/careers/{careerId}/matches/{matchId}/detail
     *
     * Returns V24DetailedMatchData if:
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
            @PathVariable String matchId) {

        if (careerId == null || careerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "careerId must not be blank")));
        }
        if (matchId == null || matchId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "matchId must not be blank")));
        }

        if (!queryService.isApiEnabled()) {
            log.debug("[V24D4C] Detail API disabled, returning 404 for careerId={}, matchId={}", careerId, matchId);
            return Mono.just(ResponseEntity.notFound().build());
        }

        return Mono.just(queryService.findDetail(careerId, matchId))
                .flatMap(optionalDetail -> {
                    if (optionalDetail.isPresent()) {
                        return Mono.just(ResponseEntity.ok(optionalDetail.get()));
                    } else {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                });
    }

    /**
     * F6 Sprint 2 (LIVE-MATCH-F6-MATCH-COMPARE):
     * GET /api/v1/careers/{careerId}/matches/{matchId}/compare
     *
     * <p>Returns a {@link MatchComparison} with the baseline (what would
     * have happened with no manager interventions), the live result, and
     * the diff. See {@link MatchComparisonService#getComparison} for the
     * algorithm.
     *
     * <p>Reuses the same feature flag as {@code /detail}
     * ({@code app.simulation.v24.expose-detail-api}).
     *
     * <p>Returns 400 if careerId or matchId is blank, 404 if:
     * <ul>
     *   <li>Feature flag is disabled.</li>
     *   <li>No live detail for the match (not finished yet, or V24 path
     *       was disabled for the career).</li>
     *   <li>No baseline state for the match (already cleaned up, or TTL
     *       7d expired).</li>
     * </ul>
     */
    @GetMapping("/{careerId}/matches/{matchId}/compare")
    public Mono<ResponseEntity<Object>> getCompare(
            @PathVariable String careerId,
            @PathVariable String matchId) {

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

        // V24D15-CLEANUP (BUG_COMPARE_404): getComparison now returns
        // Mono<MatchComparison> so it composes correctly with the Reactor
        // scheduler (the sync version was silently aborting under Reactor
        // parallel scheduling — blockOptional() threw IllegalStateException
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
                            .body(Map.of("error", e.getMessage())));
                });
    }
}