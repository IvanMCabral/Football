package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.dto.request.AssignPlayerRequest;
import com.footballmanager.adapters.in.web.career.dto.response.AssignResultDTO;
import com.footballmanager.adapters.in.web.career.dto.response.SessionPlayerDTO;
import com.footballmanager.adapters.in.web.career.mappers.SessionEntityMapper;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.career.CareerPlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * CareerPlayerController - Player management endpoints.
 * Base path: /api/v1/career/players
 */
@RestController
@RequestMapping("/api/v1/career/players")
public class CareerPlayerController {

    private final CareerPlayerService playerService;
    private final ControllerHelper controllerHelper;

    public CareerPlayerController(CareerPlayerService playerService, ControllerHelper controllerHelper) {
        this.playerService = playerService;
        this.controllerHelper = controllerHelper;
    }

    @GetMapping("/free")
    public Mono<List<SessionPlayerDTO>> getFreePlayers(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerService.getFreePlayers(userId)
                .map(players -> players.stream().map(SessionEntityMapper::toDTO).toList());
    }

    @SuppressWarnings("deprecation")
    @PostMapping("/assign")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AssignResultDTO> assignPlayerToUserTeam(@RequestBody AssignPlayerRequest request,
                                                         Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerService.assignPlayerToUserTeam(userId, request.sessionPlayerId())
                .map(career -> {
                    String userTeamId = career.getUserSessionTeamId();
                    int squadSize = userTeamId != null ? career.getTeamSquad(userTeamId).size() : 0;
                    return new AssignResultDTO(
                            "Player assigned to your team successfully",
                            squadSize,
                            career.getFreePlayers().size()
                    );
                });
    }

    @DeleteMapping("/{sessionPlayerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removePlayer(@PathVariable String sessionPlayerId,
                                   Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerService.removePlayer(userId, sessionPlayerId).then();
    }

    @GetMapping("/squad")
    public Mono<List<SessionPlayerDTO>> getUserSquad(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerService.getUserSquad(userId)
                .map(players -> players.stream().map(SessionEntityMapper::toDTO).toList());
    }
}
