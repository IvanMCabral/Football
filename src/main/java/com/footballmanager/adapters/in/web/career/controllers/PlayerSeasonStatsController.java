package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.application.service.simulation.v24.stats.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V24D6M9: Controller for player season stats API.
 *
 * <p>Three endpoints:
 * <ul>
 *   <li>GET /api/v1/careers/{careerId}/seasons/{season}/player-stats — all players</li>
 *   <li>GET /api/v1/careers/{careerId}/seasons/{season}/teams/{teamId}/player-stats — team filter</li>
 *   <li>GET /api/v1/careers/{careerId}/seasons/{season}/players/{playerId}/stats — single player</li>
 * </ul>
 *
 * <p>Pagination (all/team endpoints):
 * <ul>
 *   <li>limit: default 50, max 200, limit <= 0 → 400</li>
 *   <li>offset: default 0, offset < 0 → 400</li>
 *   <li>sortBy: goals|assists|averageRating|appearances|starts|shots|keyPasses|yellowCards|redCards|injuries|fouls|playerName (default: goals)</li>
 *   <li>order: asc|desc (default: desc)</li>
 * </ul>
 *
 * <p>Feature-gated: returns 404 when {@code app.simulation.v24.expose-detail-api=false}.
 * Does NOT enable V24 simulation, persistence, or any production simulation path.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/careers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PlayerSeasonStatsController {

    private final PlayerSeasonStatsQueryService queryService;

    public PlayerSeasonStatsController(PlayerSeasonStatsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{careerId}/seasons/{season}/player-stats")
    public Mono<ResponseEntity<Object>> getPlayerSeasonStats(
            @PathVariable String careerId,
            @PathVariable Integer season,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order) {

        if (careerId == null || careerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "careerId must not be blank")));
        }
        if (season == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "season must not be null")));
        }

        // Validation: limit <= 0 → 400
        if (limit != null && limit <= 0) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "limit must be greater than 0")));
        }
        // Validation: offset < 0 → 400
        if (offset != null && offset < 0) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "offset must not be negative")));
        }
        // Validation: invalid sortBy → 400
        if (sortBy != null && !sortBy.isBlank() && PlayerSeasonStatsSortField.fromString(sortBy) == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid sortBy field: " + sortBy)));
        }
        // Validation: invalid order → 400
        if (order != null && !order.isBlank() && !order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc")) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid order: " + order + " (must be 'asc' or 'desc')")));
        }

        if (!queryService.isApiEnabled()) {
            log.debug("[V24D6M7] Player stats API disabled, returning 404 for careerId={}, season={}", careerId, season);
            return Mono.just(ResponseEntity.notFound().build());
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

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(
                careerId, season, null, null, effectiveLimit, effectiveOffset, sortBy, order);

        // Add any clamped warnings from controller
        if (!warnings.isEmpty()) {
            List<PlayerSeasonStatsWarning> allWarnings = new ArrayList<>(warnings);
            if (response.warnings() != null) {
                allWarnings.addAll(response.warnings());
            }
            response = PlayerSeasonStatsResponse.builder()
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

        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/{careerId}/seasons/{season}/teams/{teamId}/player-stats")
    public Mono<ResponseEntity<Object>> getTeamPlayerSeasonStats(
            @PathVariable String careerId,
            @PathVariable Integer season,
            @PathVariable String teamId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order) {

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

        // Validation: limit <= 0 → 400
        if (limit != null && limit <= 0) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "limit must be greater than 0")));
        }
        // Validation: offset < 0 → 400
        if (offset != null && offset < 0) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "offset must not be negative")));
        }
        // Validation: invalid sortBy → 400
        if (sortBy != null && !sortBy.isBlank() && PlayerSeasonStatsSortField.fromString(sortBy) == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid sortBy field: " + sortBy)));
        }
        // Validation: invalid order → 400
        if (order != null && !order.isBlank() && !order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc")) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid order: " + order + " (must be 'asc' or 'desc')")));
        }

        if (!queryService.isApiEnabled()) {
            log.debug("[V24D6M7] Player stats API disabled, returning 404 for careerId={}, season={}, teamId={}",
                    careerId, season, teamId);
            return Mono.just(ResponseEntity.notFound().build());
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

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(
                careerId, season, teamId, null, effectiveLimit, effectiveOffset, sortBy, order);

        if (!warnings.isEmpty()) {
            List<PlayerSeasonStatsWarning> allWarnings = new ArrayList<>(warnings);
            if (response.warnings() != null) {
                allWarnings.addAll(response.warnings());
            }
            response = PlayerSeasonStatsResponse.builder()
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

        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/{careerId}/seasons/{season}/players/{playerId}/stats")
    public Mono<ResponseEntity<Object>> getPlayerStats(
            @PathVariable String careerId,
            @PathVariable Integer season,
            @PathVariable String playerId) {

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

        if (!queryService.isApiEnabled()) {
            log.debug("[V24D6M7] Player stats API disabled, returning 404 for careerId={}, season={}, playerId={}",
                    careerId, season, playerId);
            return Mono.just(ResponseEntity.notFound().build());
        }

        // Single-player endpoint: no pagination needed, use defaults
        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(careerId, season, null, playerId);
        if (response.playerStats().isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.ok(response));
    }
}