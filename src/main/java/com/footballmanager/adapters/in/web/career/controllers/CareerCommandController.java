package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.dto.request.CareerStartRequest;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.career.SeasonAdvancementService;
import com.footballmanager.domain.port.in.career.AdvanceRoundUseCase;
import com.footballmanager.domain.port.in.career.ContinueSeasonUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * CareerCommandController - Command endpoints para Career (escritura).
 *
 * Endpoints que modifican estado: POST, DELETE
 * Ruta base: /api/v1/career
 */
@RestController
@RequestMapping("/api/v1/career")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CareerCommandController {

    private final ControllerHelper controllerHelper;
    private final CareerSessionService sessionService;
    private final SeasonAdvancementService seasonAdvancementService;

    public CareerCommandController(
            ControllerHelper controllerHelper,
            CareerSessionService sessionService,
            SeasonAdvancementService seasonAdvancementService) {
        this.controllerHelper = controllerHelper;
        this.sessionService = sessionService;
        this.seasonAdvancementService = seasonAdvancementService;
    }

    /**
     * POST /api/v1/career/start
     * Inicia una nueva carrera para el usuario
     */
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> startCareer(
            @RequestBody CareerStartRequest request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        Integer teamsPerDivision = request.teamsPerDivision();

        Mono<?> careerMono = teamsPerDivision != null
                ? sessionService.startNewCareer(
                        userId,
                        request.leagueId(),
                        request.teamId(),
                        request.difficulty(),
                        request.gameSpeed(),
                        teamsPerDivision
                )
                : sessionService.startNewCareer(
                        userId,
                        request.leagueId(),
                        request.teamId(),
                        request.difficulty(),
                        request.gameSpeed()
                );

        return careerMono.then();
    }

    /**
     * DELETE /api/v1/career/reset
     * Elimina la carrera del usuario
     */
    @DeleteMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> resetCareer(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return sessionService.deleteCareer(userId).then();
    }

    /**
     * POST /api/v1/career/{careerId}/next-round
     * Avanza a la siguiente fecha (solo si estado es WAITING_USER)
     * Retorna información sobre el avance para el frontend.
     */
    @PostMapping("/{careerId}/next-round")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdvanceRoundUseCase.AdvanceResult> advanceToNextRound(
            @PathVariable String careerId,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        return seasonAdvancementService.advanceToNextRound(userId, careerId);
    }

    /**
     * POST /api/v1/career/continue
     * Inicia una nueva temporada (solo si torneo está en FINISHED)
     */
    @PostMapping("/continue")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ContinueSeasonUseCase.ContinueResult> continueToNewSeason(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return seasonAdvancementService.continueToNewSeason(userId);
    }

}
