package com.footballmanager.adapters.in.web;

import com.footballmanager.application.service.TeamService;
import com.footballmanager.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class TeamController {
    private final TeamService teamService;

    @PostMapping
    public Mono<ResponseEntity<TeamDTO>> createTeam(
            @RequestBody CreateTeamRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        return teamService.createTeam(
                        UserId.of(UUID.fromString(userId)),
                        request.name(),
                        request.country(),
                        request.initialBudget())
                .map(team -> ResponseEntity.status(HttpStatus.CREATED).body(mapToDTO(team)))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/{teamId}")
    public Mono<ResponseEntity<TeamDTO>> getTeam(@PathVariable String teamId) {
        return teamService.getTeam(TeamId.of(UUID.fromString(teamId)))
                .map(team -> ResponseEntity.ok(mapToDTO(team)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<TeamDTO> getUserTeams(Authentication authentication) {
        String userId = authentication.getName();
        return teamService.getUserTeams(UserId.of(UUID.fromString(userId)))
                .map(this::mapToDTO);
    }

    @GetMapping("/{teamId}/squad")
    public Flux<PlayerDTO> getTeamSquad(@PathVariable String teamId) {
        return teamService.getTeamSquad(TeamId.of(UUID.fromString(teamId)))
                .map(this::mapPlayerToDTO);
    }

    @PostMapping("/{teamId}/players/{playerId}")
    public Mono<ResponseEntity<TeamDTO>> addPlayer(
            @PathVariable String teamId,
            @PathVariable String playerId) {
        return teamService.addPlayerToTeam(
                        TeamId.of(UUID.fromString(teamId)),
                        PlayerId.of(UUID.fromString(playerId)))
                .map(team -> ResponseEntity.ok(mapToDTO(team)))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @DeleteMapping("/{teamId}/players/{playerId}")
    public Mono<ResponseEntity<Void>> removePlayer(
            @PathVariable String teamId,
            @PathVariable String playerId) {
        return teamService.removePlayerFromTeam(
                        TeamId.of(UUID.fromString(teamId)),
                        PlayerId.of(UUID.fromString(playerId)))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    private TeamDTO mapToDTO(Team team) {
        return new TeamDTO(
                team.getId().getValue().toString(),
                team.getManagerId().getValue().toString(),
                team.getName(),
                team.getCountry(),
                team.getBudget(),
                team.getFormation().name(),
                team.getSquadSize(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }

    private PlayerDTO mapPlayerToDTO(Player player) {
        return new PlayerDTO(
                player.getId().getValue().toString(),
                player.getName(),
                player.getAge(),
                player.getPosition().name(),
                player.calculateOverallRating(),
                player.getEnergy(),
                player.isInjured(),
                player.getMarketValue()
        );
    }

    public record CreateTeamRequest(
            String name,
            String country,
            BigDecimal initialBudget
    ) {}

    public record TeamDTO(
            String id,
            String managerId,
            String name,
            String country,
            BigDecimal budget,
            String formation,
            int squadSize,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}

    public record PlayerDTO(
            String id,
            String name,
            int age,
            String position,
            int overallRating,
            int energy,
            boolean injured,
            BigDecimal marketValue
    ) {}
}
