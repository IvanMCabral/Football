package com.footballmanager.adapters.in.web.career.controllers;

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
 * <p>GET /api/careers/{careerId}/matches/{matchId}/detail
 *
 * <p>Feature-gated: returns 404 when {@code app.simulation.v24.expose-detail-api=false}.
 * Does NOT enable V24 simulation, persistence, or any production simulation path.
 */
@Slf4j
@RestController
@RequestMapping("/api/careers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class V24DetailedMatchController {

    private final V24DetailedMatchQueryService queryService;

    public V24DetailedMatchController(V24DetailedMatchQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * GET /api/careers/{careerId}/matches/{matchId}/detail
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
}