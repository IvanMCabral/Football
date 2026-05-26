package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.application.service.simulation.v24.stats.PlayerSeasonStatsAggregator;
import com.footballmanager.application.service.simulation.v24.stats.PlayerSeasonStatsQueryService;
import com.footballmanager.application.service.simulation.v24.stats.PlayerSeasonStatsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * V24D6M4: Controller for player season stats API.
 *
 * <p>Three endpoints:
 * <ul>
 *   <li>GET /api/careers/{careerId}/seasons/{season}/player-stats — all players</li>
 *   <li>GET /api/careers/{careerId}/seasons/{season}/teams/{teamId}/player-stats — team filter</li>
 *   <li>GET /api/careers/{careerId}/seasons/{season}/players/{playerId}/stats — single player</li>
 * </ul>
 *
 * <p>Feature-gated: returns 404 when {@code app.simulation.v24.expose-detail-api=false}.
 * Does NOT enable V24 simulation, persistence, or any production simulation path.
 */
@Slf4j
@RestController
@RequestMapping("/api/careers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PlayerSeasonStatsController {

    private final PlayerSeasonStatsQueryService queryService;

    public PlayerSeasonStatsController(PlayerSeasonStatsQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * GET /api/careers/{careerId}/seasons/{season}/player-stats
     *
     * Returns stats for all players across all teams in the given season.
     */
    @GetMapping("/{careerId}/seasons/{season}/player-stats")
    public Mono<ResponseEntity<Object>> getPlayerSeasonStats(
            @PathVariable String careerId,
            @PathVariable Integer season) {

        if (careerId == null || careerId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "careerId must not be blank")));
        }
        if (season == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "season must not be null")));
        }

        if (!queryService.isApiEnabled()) {
            log.debug("[V24D6M4] Player stats API disabled, returning 404 for careerId={}, season={}", careerId, season);
            return Mono.just(ResponseEntity.notFound().build());
        }

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(careerId, season);
        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * GET /api/careers/{careerId}/seasons/{season}/teams/{teamId}/player-stats
     *
     * Returns stats for all players on a specific team in the given season.
     */
    @GetMapping("/{careerId}/seasons/{season}/teams/{teamId}/player-stats")
    public Mono<ResponseEntity<Object>> getTeamPlayerSeasonStats(
            @PathVariable String careerId,
            @PathVariable Integer season,
            @PathVariable String teamId) {

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

        if (!queryService.isApiEnabled()) {
            log.debug("[V24D6M4] Player stats API disabled, returning 404 for careerId={}, season={}, teamId={}",
                    careerId, season, teamId);
            return Mono.just(ResponseEntity.notFound().build());
        }

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(careerId, season, teamId, null, null);
        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * GET /api/careers/{careerId}/seasons/{season}/players/{playerId}/stats
     *
     * Returns stats for a specific player in the given season.
     */
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
            log.debug("[V24D6M4] Player stats API disabled, returning 404 for careerId={}, season={}, playerId={}",
                    careerId, season, playerId);
            return Mono.just(ResponseEntity.notFound().build());
        }

        PlayerSeasonStatsResponse response = queryService.getPlayerSeasonStats(careerId, season, null, playerId, null);
        if (response.playerStats().isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.ok(response));
    }
}