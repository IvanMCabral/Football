package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.lineup.dto.*;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.port.in.lineup.LineupCommandUseCase;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * LineupController - Endpoints para gestionar el Starting XI
 * Base path: /api/v1/career/lineup
 *
 * GUARD CLAUSES: Los endpoints de escritura requieren careerPhase = PRE_MATCH
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/career/lineup")
@RequiredArgsConstructor
public class LineupController {

    private final LineupCommandUseCase lineupCommandUseCase;
    private final LineupQueryUseCase lineupQueryUseCase;
    private final CareerSessionService careerSessionService;

    /**
     * Auto-seleccionar Starting XI basado en OVR
     * POST /api/v1/career/lineup/auto-select
     * Body: { "formation": "4-4-2" }
     */
    @PostMapping("/auto-select")
    public Mono<LineupDTO> autoSelectLineup(@RequestBody AutoSelectRequest request,
                                            Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return careerSessionService.getCareerFromCache(userId)
            .flatMap(career -> {
                CareerPhase phase = career.getTournamentState().getCareerPhase();
                if (phase != CareerPhase.PRE_MATCH && phase != CareerPhase.WAITING_USER) {
                    log.warn("[LINEUP-CONTROLLER] Rejected auto-select: careerPhase={}, expected PRE_MATCH or WAITING_USER", phase);
                    return Mono.error(new IllegalStateException(
                        "No se puede modificar lineup. La fase actual es " + phase + ". Solo se permite en PRE_MATCH o WAITING_USER."));
                }
                return lineupCommandUseCase.autoSelectLineup(userId, request.formation());
            });
    }

    /**
     * Selección manual del Starting XI
     * POST /api/v1/career/lineup/manual-select
     * Body: { "formation": "4-4-2", "playerIds": ["id1", "id2", ...] }
     */
    @PostMapping("/manual-select")
    public Mono<LineupDTO> manualSelectLineup(@RequestBody ManualSelectRequest request,
                                              Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return careerSessionService.getCareerFromCache(userId)
            .flatMap(career -> {
                CareerPhase phase = career.getTournamentState().getCareerPhase();
                if (phase != CareerPhase.PRE_MATCH && phase != CareerPhase.WAITING_USER) {
                    log.warn("[LINEUP-CONTROLLER] Rejected manual-select: careerPhase={}, expected PRE_MATCH or WAITING_USER", phase);
                    return Mono.error(new IllegalStateException(
                        "No se puede modificar lineup. La fase actual es " + phase + ". Solo se permite en PRE_MATCH o WAITING_USER."));
                }
                return lineupCommandUseCase.manualSelectLineup(userId, request.formation(), request.playerIds());
            });
    }

    /**
     * Confirmar lineup para iniciar partido
     * POST /api/v1/career/lineup/confirm
     *
     * Permite WAITING_USER (después de terminar ronda, antes de llamar next-round)
     * y PRE_MATCH (después de llamar next-round)
     */
    @PostMapping("/confirm")
    public Mono<Void> confirmLineup(Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return careerSessionService.getCareerFromCache(userId)
            .flatMap(career -> {
                CareerPhase phase = career.getTournamentState().getCareerPhase();
                if (phase != CareerPhase.PRE_MATCH && phase != CareerPhase.WAITING_USER) {
                    log.warn("[LINEUP-CONTROLLER] Rejected confirm: careerPhase={}, expected PRE_MATCH or WAITING_USER", phase);
                    return Mono.error(new IllegalStateException(
                        "No se puede confirmar lineup. La fase actual es " + phase + ". Solo se permite en PRE_MATCH o WAITING_USER."));
                }
                return lineupCommandUseCase.confirmLineup(userId);
            });
    }

    /**
     * Obtener lineup actual
     * GET /api/v1/career/lineup/current
     */
    @GetMapping("/current")
    public Mono<LineupDTO> getCurrentLineup(Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        return lineupQueryUseCase.getCurrentLineup(userId);
    }

    private UUID getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthorized: no user id in authentication");
        }
        return UUID.fromString(authentication.getName());
    }
}
