package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.application.service.simulation.detailed.stats.*;
import com.footballmanager.application.service.security.CareerOwnershipAuthority;
import com.footballmanager.application.service.security.CareerOwnershipDeniedException;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * <p>Three endpoints:
 * <ul>
 *   <li>GET /api/v1/careers/{careerId}/seasons/{season}/player-stats â€” all players</li>
 *   <li>GET /api/v1/careers/{careerId}/seasons/{season}/teams/{teamId}/player-stats â€” team filter</li>
 *   <li>GET /api/v1/careers/{careerId}/seasons/{season}/players/{playerId}/stats â€” single player</li>
 * </ul>
 *
 * <p>Pagination (all/team endpoints):
 * <ul>
 *   <li>limit: default 50, max 200, limit <= 0 â†’ 400</li>
 *   <li>offset: default 0, offset < 0 â†’ 400</li>
 *   <li>sortBy: goals|assists|averageRating|appearances|starts|shots|keyPasses|yellowCards|redCards|injuries|fouls|playerName (default: goals)</li>
 *   <li>order: asc|desc (default: desc)</li>
 * </ul>
 *
 * <p>Feature-gated: returns 404 when {@code app.simulation.detailed.expose-detail-api=false}.
 * Does NOT enable detailed match simulation, persistence, or any production simulation path.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/careers")
public class PlayerSeasonStatsController {

    private final PlayerSeasonStatsQueryService queryService;
    private final CareerOwnershipAuthority ownershipAuthority;
    private final ControllerHelper controllerHelper;

    public PlayerSeasonStatsController(PlayerSeasonStatsQueryService queryService) {
        this(queryService, null, null);
    }

    @Autowired
    public PlayerSeasonStatsController(PlayerSeasonStatsQueryService queryService,
                                       CareerOwnershipAuthority ownershipAuthority,
                                       ControllerHelper controllerHelper) {
        this.queryService = queryService;
        this.ownershipAuthority = ownershipAuthority;
        this.controllerHelper = controllerHelper;
    }

    @GetMapping("/{careerId}/seasons/{season}/player-stats")
    public Mono<ResponseEntity<Object>> getPlayerSeasonStats(
            @PathVariable String careerId,
            @PathVariable Integer season,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order,
            Authentication authentication) {
        return getPlayerSeasonStatsInternal(careerId, season, limit, offset, sortBy, order, authentication);
    }

    public Mono<ResponseEntity<Object>> getPlayerSeasonStats(
            String careerId, Integer season, Integer limit, Integer offset, String sortBy, String order) {
        return getPlayerSeasonStatsInternal(careerId, season, limit, offset, sortBy, order, null);
    }

    private Mono<ResponseEntity<Object>> getPlayerSeasonStatsInternal(
            String careerId, Integer season, Integer limit, Integer offset, String sortBy, String order,
            Authentication authentication) {

        if (careerId == null || careerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "careerId must not be blank")));
        }
        if (season == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "season must not be null")));
        }

        // Validation: limit <= 0 â†’ 400
        if (limit != null && limit <= 0) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "limit must be greater than 0")));
        }
        // Validation: offset < 0 â†’ 400
        if (offset != null && offset < 0) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "offset must not be negative")));
        }
        // Validation: invalid sortBy â†’ 400
        if (sortBy != null && !sortBy.isBlank() && PlayerSeasonStatsSortField.fromString(sortBy) == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid sortBy field: " + sortBy)));
        }
        // Validation: invalid order â†’ 400
        if (order != null && !order.isBlank() && !order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc")) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid order: " + order + " (must be 'asc' or 'desc')")));
        }

        int effectiveLimit = limit != null ? limit : 50;
        int effectiveOffset = offset != null ? offset : 0;
        // Clamp limit > 200 and track warning
        List<PlayerSeasonStatsWarning> warnings = new ArrayList<>();
        if (limit != null && limit > 200) {
            effectiveLimit = 200;
            warnings.add(new PlayerSeasonStatsWarning(
                    PlayerSeasonStatsWarningCode.LARGE_LIMIT_CLAMPED,
                    "limit was greater than max and was clamped to 200",
                    "limit"));
        }
        final int queryLimit = effectiveLimit;
        final int queryOffset = effectiveOffset;

        return authorize(careerId, authentication)
                .then(Mono.defer(() -> {
                    if (!queryService.isApiEnabled()) {
                        log.debug("Player stats API disabled, returning 404 for careerId={}, season={}", careerId, season);
                        return Mono.just(ResponseEntity.<Object>notFound().build());
                    }
                    return queryService.getPlayerSeasonStats(
                                    careerId, season, null, null, queryLimit, queryOffset, sortBy, order)
                            .map(response -> withWarnings(response, warnings))
                            .map(response -> ResponseEntity.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body((Object) response));
                }))
                .onErrorResume(CareerOwnershipDeniedException.class,
                        ignored -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/{careerId}/seasons/{season}/teams/{teamId}/player-stats")
    public Mono<ResponseEntity<Object>> getTeamPlayerSeasonStats(
            @PathVariable String careerId,
            @PathVariable Integer season,
            @PathVariable String teamId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order,
            Authentication authentication) {
        return getTeamPlayerSeasonStatsInternal(careerId, season, teamId, limit, offset, sortBy, order, authentication);
    }

    public Mono<ResponseEntity<Object>> getTeamPlayerSeasonStats(
            String careerId, Integer season, String teamId, Integer limit, Integer offset,
            String sortBy, String order) {
        return getTeamPlayerSeasonStatsInternal(careerId, season, teamId, limit, offset, sortBy, order, null);
    }

    private Mono<ResponseEntity<Object>> getTeamPlayerSeasonStatsInternal(
            String careerId, Integer season, String teamId, Integer limit, Integer offset,
            String sortBy, String order, Authentication authentication) {

        if (careerId == null || careerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "careerId must not be blank")));
        }
        if (season == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "season must not be null")));
        }
        if (teamId == null || teamId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "teamId must not be blank")));
        }

        // Validation: limit <= 0 â†’ 400
        if (limit != null && limit <= 0) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "limit must be greater than 0")));
        }
        // Validation: offset < 0 â†’ 400
        if (offset != null && offset < 0) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "offset must not be negative")));
        }
        // Validation: invalid sortBy â†’ 400
        if (sortBy != null && !sortBy.isBlank() && PlayerSeasonStatsSortField.fromString(sortBy) == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid sortBy field: " + sortBy)));
        }
        // Validation: invalid order â†’ 400
        if (order != null && !order.isBlank() && !order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc")) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid order: " + order + " (must be 'asc' or 'desc')")));
        }

        int effectiveLimit = limit != null ? limit : 50;
        int effectiveOffset = offset != null ? offset : 0;
        List<PlayerSeasonStatsWarning> warnings = new ArrayList<>();
        if (limit != null && limit > 200) {
            effectiveLimit = 200;
            warnings.add(new PlayerSeasonStatsWarning(
                    PlayerSeasonStatsWarningCode.LARGE_LIMIT_CLAMPED,
                    "limit was greater than max and was clamped to 200",
                    "limit"));
        }
        final int queryLimit = effectiveLimit;
        final int queryOffset = effectiveOffset;

        return authorize(careerId, authentication)
                .then(Mono.defer(() -> {
                    if (!queryService.isApiEnabled()) {
                        log.debug("Player stats API disabled, returning 404 for careerId={}, season={}, teamId={}",
                                careerId, season, teamId);
                        return Mono.just(ResponseEntity.<Object>notFound().build());
                    }
                    return queryService.getPlayerSeasonStats(
                                    careerId, season, teamId, null, queryLimit, queryOffset, sortBy, order)
                            .map(response -> withWarnings(response, warnings))
                            .map(response -> ResponseEntity.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body((Object) response));
                }))
                .onErrorResume(CareerOwnershipDeniedException.class,
                        ignored -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/{careerId}/seasons/{season}/players/{playerId}/stats")
    public Mono<ResponseEntity<Object>> getPlayerStats(
            @PathVariable String careerId,
            @PathVariable Integer season,
            @PathVariable String playerId,
            Authentication authentication) {
        return getPlayerStatsInternal(careerId, season, playerId, authentication);
    }

    public Mono<ResponseEntity<Object>> getPlayerStats(String careerId, Integer season, String playerId) {
        return getPlayerStatsInternal(careerId, season, playerId, null);
    }

    private Mono<ResponseEntity<Object>> getPlayerStatsInternal(
            String careerId, Integer season, String playerId, Authentication authentication) {

        if (careerId == null || careerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "careerId must not be blank")));
        }
        if (season == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "season must not be null")));
        }
        if (playerId == null || playerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "playerId must not be blank")));
        }

        return authorize(careerId, authentication)
                .then(Mono.defer(() -> {
                    if (!queryService.isApiEnabled()) {
                        log.debug("Player stats API disabled, returning 404 for careerId={}, season={}, playerId={}",
                                careerId, season, playerId);
                        return Mono.just(ResponseEntity.<Object>notFound().build());
                    }
                    return queryService.getPlayerSeasonStats(careerId, season, null, playerId)
                            .map(response -> response.playerStats().isEmpty()
                                    ? ResponseEntity.notFound().build()
                                    : ResponseEntity.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .body((Object) response));
                }))
                .onErrorResume(CareerOwnershipDeniedException.class,
                        ignored -> Mono.just(ResponseEntity.notFound().build()));
    }

    private PlayerSeasonStatsResponse withWarnings(
            PlayerSeasonStatsResponse response,
            List<PlayerSeasonStatsWarning> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return response;
        }
        List<PlayerSeasonStatsWarning> allWarnings = new ArrayList<>(warnings);
        if (response.warnings() != null) {
            allWarnings.addAll(response.warnings());
        }
        return PlayerSeasonStatsResponse.builder()
                .careerId(response.careerId())
                .season(response.season())
                .playerStats(response.playerStats())
                .totalGoals(response.totalGoals())
                .totalAssists(response.totalAssists())
                .totalAppearances(response.totalAppearances())
                .averageRating(response.averageRating())
                .incomplete(response.incomplete())
                .message(response.message())
                .metadata(response.metadata())
                .warnings(allWarnings)
                .build();
    }

    private Mono<Void> authorize(String careerId, Authentication authentication) {
        if (authentication == null || ownershipAuthority == null) {
            return Mono.empty();
        }
        return ownershipAuthority.requireOwned(controllerHelper.getUserId(authentication), careerId).then();
    }
}
