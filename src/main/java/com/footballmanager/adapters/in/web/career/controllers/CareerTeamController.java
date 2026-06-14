package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.dto.request.AssignPlayerToTeamRequest;
import com.footballmanager.adapters.in.web.career.dto.response.AssignResultDTO;
import com.footballmanager.adapters.in.web.career.dto.response.SessionPlayerDTO;
import com.footballmanager.adapters.in.web.career.dto.response.SessionTeamDTO;
import com.footballmanager.adapters.in.web.career.mappers.SessionEntityMapper;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.port.in.career.PlayerAssignmentUseCase;
import com.footballmanager.domain.port.in.career.SquadQueryUseCase;
import com.footballmanager.domain.port.in.career.TeamCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * CareerTeamController - Team management endpoints.
 * Base path: /api/v1/career/teams
 *
 * Usa UseCases para lógica de negocio:
 * - TeamCommandUseCase: Operaciones sobre equipos
 * - PlayerAssignmentUseCase: Asignación de jugadores
 * - SquadQueryUseCase: Resolución de squads
 */
@RestController
@RequestMapping("/api/v1/career/teams")
@RequiredArgsConstructor
public class CareerTeamController {

    private final TeamCommandUseCase teamCommandUseCase;
    private final PlayerAssignmentUseCase playerAssignmentUseCase;
    private final SquadQueryUseCase squadQueryUseCase;
    private final CareerSessionService sessionService;

    @PostMapping("/random")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SessionTeamDTO> createRandomTeam(Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return teamCommandUseCase.createRandomTeam(userId)
            .flatMap(career -> Mono.just(SessionEntityMapper.toDTO(
                career.getAllSessionTeams().get(career.getAllSessionTeams().size() - 1))));
    }

    @PostMapping("/clone/{realTeamId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SessionTeamDTO> cloneTeamToSession(@PathVariable UUID realTeamId,
                                                    Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return teamCommandUseCase.cloneTeamToSession(userId, realTeamId)
            .flatMap(career -> Mono.just(SessionEntityMapper.toDTO(
                career.getAllSessionTeams().get(career.getAllSessionTeams().size() - 1))));
    }

    @GetMapping("")
    public Mono<List<SessionTeamDTO>> getSessionTeams(Authentication authentication) {
        return teamCommandUseCase.getSessionTeams(getUserIdFromAuth(authentication))
            .map(teams -> teams.stream().map(SessionEntityMapper::toDTO).toList());
    }

    @GetMapping("/{sessionTeamId}")
    public Mono<SessionTeamDTO> getSessionTeam(@PathVariable String sessionTeamId,
                                                Authentication authentication) {
        return teamCommandUseCase.getSessionTeam(getUserIdFromAuth(authentication), sessionTeamId)
            .map(SessionEntityMapper::toDTO);
    }

    @DeleteMapping("/{sessionTeamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeSessionTeam(@PathVariable String sessionTeamId,
                                         Authentication authentication) {
        return teamCommandUseCase.removeSessionTeam(getUserIdFromAuth(authentication), sessionTeamId).then();
    }

    @PostMapping("/{sessionTeamId}/players")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AssignResultDTO> assignPlayerToSessionTeam(
            @PathVariable String sessionTeamId,
            @RequestBody AssignPlayerToTeamRequest request,
            Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return playerAssignmentUseCase.assignPlayerToSessionTeam(userId, request.sessionPlayerId(), sessionTeamId)
            .map(career -> new AssignResultDTO("Player assigned to team successfully"));
    }

    @DeleteMapping("/{sessionTeamId}/players/{sessionPlayerId}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AssignResultDTO> removePlayerFromSessionTeam(
            @PathVariable String sessionTeamId,
            @PathVariable String sessionPlayerId,
            Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return playerAssignmentUseCase.removePlayerFromSessionTeam(userId, sessionPlayerId, sessionTeamId)
            .map(career -> new AssignResultDTO("Player removed from team successfully"));
    }

    @GetMapping("/{sessionTeamId}/squad")
    public Mono<List<SessionPlayerDTO>> getSessionTeamSquad(@PathVariable String sessionTeamId,
                                                             Authentication authentication) {
        return squadQueryUseCase.getSessionTeamSquad(getUserIdFromAuth(authentication), sessionTeamId)
            .map(players -> players.stream().map(SessionEntityMapper::toDTO).toList());
    }

    @GetMapping("/by-world/{worldTeamId}/squad")
    public Mono<List<SessionPlayerDTO>> getTeamSquadByWorldId(@PathVariable String worldTeamId,
                                                               Authentication authentication) {
        return squadQueryUseCase.getTeamSquadByWorldTeamId(getUserIdFromAuth(authentication), worldTeamId)
            .map(players -> players.stream().map(SessionEntityMapper::toDTO).toList());
    }

    /**
     * GET /api/v1/career/teams/me
     * Obtiene el equipo del usuario.
     */
    @GetMapping("/me")
    public Mono<SessionTeamDTO> getMyTeam(Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return sessionService.continueCareer(userId)
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                if (userTeamId == null) {
                    return Mono.empty();
                }
                return Mono.defer(() -> Mono.justOrEmpty(
                    teamCommandUseCase.getSessionTeam(userId, userTeamId)
                        .map(SessionEntityMapper::toDTO)
                        .block()));
            });
    }

    /**
     * GET /api/v1/career/teams/me/squad
     * Obtiene el squad del equipo del usuario.
     */
    @GetMapping("/me/squad")
    public Mono<List<SessionPlayerDTO>> getMyTeamSquad(Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return sessionService.continueCareer(userId)
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                if (userTeamId == null) {
                    return Mono.<List<SessionPlayerDTO>>empty();
                }
                return squadQueryUseCase.getSessionTeamSquad(userId, userTeamId)
                    .map(players -> players.stream().map(SessionEntityMapper::toDTO).toList());
            })
            .switchIfEmpty(Mono.just(List.of()));
    }

    private UUID getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthorized: no user id in authentication");
        }
        return UUID.fromString(authentication.getName());
    }
}
