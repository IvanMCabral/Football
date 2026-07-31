package com.footballmanager.adapters.in.web.editor;

import com.footballmanager.adapters.in.web.world.dto.*;
import com.footballmanager.application.service.world.*;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.in.player.AssignPlayerUseCase;
import com.footballmanager.domain.ports.in.player.RemovePlayerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/editor")
@RequiredArgsConstructor
@Profile({"dev", "local", "test"})
public class EditorController {

    private final WorldTeamCommandService teamCommandService;
    private final WorldPlayerCommandService playerCommandService;
    private final WorldQueryService queryService;
    private final AssignPlayerUseCase assignPlayerUseCase;
    private final RemovePlayerUseCase removePlayerUseCase;

    /**
     * POST /api/v1/editor/custom-player
     * Crea jugador custom en WorldSnapshot
     */
    @PostMapping("/custom-player")
    public Mono<ResponseEntity<WorldSnapshot>> createCustomPlayer(
            @RequestBody CreateCustomPlayerRequest request,
            Authentication authentication) {

        UUID userId = authenticatedUserId(authentication);

        return playerCommandService.createCustomPlayer(
                        userId, request.name(), request.age(), request.position(),
                        request.attack(), request.defense(), request.technique(),
                        request.speed(), request.stamina(), request.mentality()
                )
                .map(ResponseEntity::ok);
    }

    /**
     * POST /api/v1/editor/custom-team
     * Crea equipo custom en WorldSnapshot
     */
    @PostMapping("/custom-team")
    public Mono<ResponseEntity<WorldSnapshot>> createCustomTeam(
            @RequestBody CreateCustomTeamRequest request,
            Authentication authentication) {

        UUID userId = authenticatedUserId(authentication);

        return teamCommandService.createCustomTeam(
                        userId, request.name(), request.country(),
                        request.budget(), request.formation()
                )
                .map(ResponseEntity::ok);
    }

    /**
     * POST /api/v1/editor/random-team
     * Genera 1 equipo random en WorldSnapshot
     */
    @PostMapping("/random-team")
    public Mono<ResponseEntity<WorldTeam>> createRandomTeam(Authentication authentication) {
        UUID userId = authenticatedUserId(authentication);
        return teamCommandService.createRandomTeam(userId)
                .map(snapshot -> snapshot.getAllWorldTeams().stream()
                        .reduce((first, second) -> second)
                        .orElseThrow(() -> new IllegalStateException("No team created")))
                .map(ResponseEntity::ok);
    }

    /**
     * POST /api/v1/editor/random-teams
     * Genera N equipos random en WorldSnapshot
     */
    @PostMapping("/random-teams")
    public Mono<ResponseEntity<RandomTeamsResponse>> createRandomTeams(
            @RequestBody RandomTeamsRequest request,
            Authentication authentication) {
        UUID userId = authenticatedUserId(authentication);
        return teamCommandService.createRandomTeams(userId, request.count())
                .map(snapshot -> {
                    RandomTeamsResponse response = new RandomTeamsResponse(
                            request.count(),
                            "Successfully generated " + request.count() + " random teams in WorldSnapshot"
                    );
                    return ResponseEntity.ok(response);
                });
    }

    /**
     * POST /api/v1/editor/assign-player
     * Asigna jugador a equipo en WorldSnapshot
     */
    @PostMapping("/assign-player")
    public Mono<ResponseEntity<WorldSnapshot>> assignPlayerToTeam(
            @RequestBody AssignPlayerRequest request,
            Authentication authentication) {

        UUID userId = authenticatedUserId(authentication);

        return assignPlayerUseCase.execute(userId, request.playerId(), request.teamId())
                .map(ResponseEntity::ok);
    }

    /**
     * POST /api/v1/editor/remove-player
     * Remueve jugador de equipo en WorldSnapshot
     */
    @PostMapping("/remove-player")
    public Mono<ResponseEntity<WorldSnapshot>> removePlayerFromTeam(
            @RequestBody RemovePlayerRequest request,
            Authentication authentication) {

        UUID userId = authenticatedUserId(authentication);

        return removePlayerUseCase.execute(userId, request.playerId())
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/editor/free-players
     * Obtiene jugadores libres (sin equipo)
     */
    @GetMapping("/free-players")
    public Mono<ResponseEntity<List<WorldPlayer>>> getFreePlayers(
            Authentication authentication) {

        UUID userId = authenticatedUserId(authentication);

        return queryService.getFreePlayers(userId)
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/editor/teams
     * Obtiene todos los equipos disponibles para el editor (incluye equipos sin liga)
     */
    @GetMapping("/teams")
    public Mono<ResponseEntity<List<WorldTeam>>> getAllTeams(
            Authentication authentication) {

        UUID userId = authenticatedUserId(authentication);

        return queryService.getAllTeamsForEditor(userId)
                .map(ResponseEntity::ok);
    }

    private UUID authenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return UUID.fromString(authentication.getName());
    }
}
