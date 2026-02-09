package com.footballmanager.adapters.in.web.world;

import com.footballmanager.adapters.in.web.world.dto.DivisionPreview;
import com.footballmanager.adapters.in.web.world.dto.TeamWithOVR;
import com.footballmanager.application.service.query.DivisionPreviewService;
import com.footballmanager.application.service.query.TeamOVRQueryService;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.in.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * World API - Consultas sobre el mundo.
 *
 * Endpoints principales para obtener ligas, equipos y jugadores.
 * Los equipos custom (sin liga) están disponibles en /api/v1/editor/teams
 */
@RestController
@RequestMapping("/api/v1/world")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class WorldQueryController {

    private final GetLeaguesUseCase getLeaguesUseCase;
    private final GetTeamsByLeagueUseCase getTeamsByLeagueUseCase;
    private final GetAllTeamsUseCase getAllTeamsUseCase;
    private final GetPlayersByTeamUseCase getPlayersByTeamUseCase;
    private final GetAllPlayersUseCase getAllPlayersUseCase;
    private final GetFreePlayersUseCase getFreePlayersUseCase;
    private final TeamOVRQueryService teamOVRQueryService;
    private final DivisionPreviewService divisionPreviewService;

    /**
     * GET /api/v1/world/leagues?userId={userId}
     */
    @GetMapping("/leagues")
    public Mono<ResponseEntity<List<WorldLeague>>> getLeagues(@RequestParam UUID userId) {
        return getLeaguesUseCase.execute(userId)
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/world/leagues/{leagueId}/teams?userId={userId}
     */
    @GetMapping("/leagues/{leagueId}/teams")
    public Mono<ResponseEntity<List<WorldTeam>>> getTeamsByLeague(
            @PathVariable UUID leagueId,
            @RequestParam UUID userId) {
        return getTeamsByLeagueUseCase.execute(userId, leagueId)
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/world/leagues/{leagueId}/teams-with-ovr?userId={userId}
     */
    @GetMapping("/leagues/{leagueId}/teams-with-ovr")
    public Mono<ResponseEntity<List<TeamWithOVR>>> getTeamsByLeagueWithOVR(
            @PathVariable UUID leagueId,
            @RequestParam UUID userId) {
        return getTeamsByLeagueUseCase.execute(userId, leagueId)
                .flatMap(teams -> teamOVRQueryService.buildTeamsWithOVR(userId, teams))
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/world/leagues/{leagueId}/division-preview?teamsPerDivision={N}&userId={userId}
     */
    @GetMapping("/leagues/{leagueId}/division-preview")
    public Mono<ResponseEntity<List<DivisionPreview>>> getDivisionPreview(
            @PathVariable UUID leagueId,
            @RequestParam int teamsPerDivision,
            @RequestParam UUID userId) {
        return getTeamsByLeagueUseCase.execute(userId, leagueId)
                .flatMap(teams -> teamOVRQueryService.buildTeamsWithOVR(userId, teams))
                .map(teamsWithOVR -> {
                    List<DivisionPreview> previews = divisionPreviewService.calculateDivisionPreview(teamsWithOVR, teamsPerDivision);
                    return ResponseEntity.ok(previews);
                });
    }

    /**
     * GET /api/v1/world/teams?userId={userId}
     */
    @GetMapping("/teams")
    public Mono<ResponseEntity<List<WorldTeam>>> getAllTeams(@RequestParam UUID userId) {
        return getAllTeamsUseCase.execute(userId)
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/world/teams/{worldTeamId}/players?userId={userId}
     */
    @GetMapping("/teams/{worldTeamId}/players")
    public Mono<ResponseEntity<List<WorldPlayer>>> getPlayersByTeam(
            @PathVariable String worldTeamId,
            @RequestParam UUID userId) {
        return getPlayersByTeamUseCase.execute(userId, worldTeamId)
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/world/players?userId={userId}
     */
    @GetMapping("/players")
    public Mono<ResponseEntity<List<WorldPlayer>>> getAllPlayers(@RequestParam UUID userId) {
        return getAllPlayersUseCase.execute(userId)
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/world/free-players?userId={userId}
     */
    @GetMapping("/free-players")
    public Mono<ResponseEntity<List<WorldPlayer>>> getFreePlayers(@RequestParam UUID userId) {
        return getFreePlayersUseCase.execute(userId)
                .map(ResponseEntity::ok);
    }
}
