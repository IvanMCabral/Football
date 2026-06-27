package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.lineup.dto.*;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.TeamChemistryCalculator;
import com.footballmanager.domain.port.in.lineup.LineupCommandUseCase;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ControllerHelper controllerHelper;

    /**
     * Auto-seleccionar Starting XI basado en OVR
     * POST /api/v1/career/lineup/auto-select
     * Body: { "formation": "4-4-2" }
     */
    @PostMapping("/auto-select")
    public Mono<LineupDTO> autoSelectLineup(@RequestBody AutoSelectRequest request,
                                            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
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
     * Body: { "formation": "4-4-2", "playerIds": ["id1", "id2", ...],
     *         "slots": [{ "playerId": "id1", "subdivisionId": "S22-1" }, ...] }
     *
     * <p>El campo {@code slots} es opcional. Si está presente, persiste
     * la subdivisionId por jugador (sprint MVP1-lineup-cancha-1).
     * Si está ausente, se aplica backward compat: el front infiere
     * subdivisionId del role del jugador.
     */
    @PostMapping("/manual-select")
    public Mono<LineupDTO> manualSelectLineup(@RequestBody ManualSelectRequest request,
                                              Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return careerSessionService.getCareerFromCache(userId)
            .flatMap(career -> {
                CareerPhase phase = career.getTournamentState().getCareerPhase();
                if (phase != CareerPhase.PRE_MATCH && phase != CareerPhase.WAITING_USER) {
                    log.warn("[LINEUP-CONTROLLER] Rejected manual-select: careerPhase={}, expected PRE_MATCH or WAITING_USER", phase);
                    return Mono.error(new IllegalStateException(
                        "No se puede modificar lineup. La fase actual es " + phase + ". Solo se permite en PRE_MATCH o WAITING_USER."));
                }
                return lineupCommandUseCase.manualSelectLineupWithSlots(
                    userId,
                    request.formation(),
                    request.playerIds(),
                    request.slots());
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
        UUID userId = controllerHelper.getUserId(authentication);
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
        UUID userId = controllerHelper.getUserId(authentication);
        return lineupQueryUseCase.getCurrentLineup(userId);
    }

    /**
     * V25D45 (Sprint C10): Preview de chemistry para un lineup hipotético
     * (sin guardar).
     * <p>POST /api/v1/career/lineup/preview-chemistry
     * <p>Body: {@code { "playerIds": ["id1", ..., "id11"] }}
     * <p>Response: {@link ChemistryDetail} con score + breakdown + maxSkillByType
     * + coveragePercentage calculado en vivo por
     * {@link TeamChemistryCalculator#calculate(java.util.List)}.
     *
     * <p><b>NO persiste nada</b> — es read-only. El manager lo llama mientras
     * edita el lineup (drag-and-drop en el modal visual) para ver el chemistry
     * proyectado antes de confirmar. El back ya tiene los 11 SessionPlayers
     * del career en Redis (cache); solo los recuperamos por playerId.
     *
     * <p><b>Validaciones:</b>
     * <ul>
     *   <li>{@code playerIds} debe contener exactamente 11 elementos
     *       (validado en el record {@link PreviewChemistryRequest} ctor → 400).</li>
     *   <li>Cada playerId debe existir en el career del user → si alguno falta,
     *       retornamos 404 con el id faltante en el body (defensivo: el manager
     *       no debería poder mandar ids inválidos, pero si pasa, no crasheamos).</li>
     * </ul>
     *
     * <p><b>Por qué no usamos el LineupQueryUseCase directamente:</b> el preview
     * recibe un lineup arbitrario del user, no el persistido. No tiene sentido
     * pasar por un use case de "leer lineup actual" porque NO estamos leyendo
     * el persistido — estamos computando uno hipotético.
     */
    @PostMapping("/preview-chemistry")
    public Mono<ResponseEntity<?> > previewChemistry(@RequestBody PreviewChemistryRequest request,
                                                     Authentication authentication) {
        // Validación de size (11) ya está en el ctor del record. Si falla,
        // Spring devuelve 400 automáticamente con el mensaje.
        UUID userId = controllerHelper.getUserId(authentication);
        return careerSessionService.getCareerFromCache(userId)
            .<ResponseEntity<?>>flatMap(career -> {
                Map<String, SessionPlayer> allPlayers = career.getSessionPlayers();
                if (allPlayers == null || allPlayers.isEmpty()) {
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of("error", "No players found in career", "userId", userId.toString())));
                }

                List<SessionPlayer> lineup = new ArrayList<>(11);
                List<String> missing = new ArrayList<>();
                for (String id : request.playerIds()) {
                    SessionPlayer p = allPlayers.get(id);
                    if (p == null) {
                        missing.add(id);
                    } else {
                        lineup.add(p);
                    }
                }

                if (!missing.isEmpty()) {
                    // 404 con los ids faltantes — front puede log y mostrar fallback.
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of(
                            "error", "Some playerIds not found in career",
                            "missing", missing)));
                }

                // Compute ChemistryDetail (V25D41/C6 — same TeamChemistryCalculator).
                // lineup.size() == 11 garantizado (11 ids válidos, request validó size 11).
                ChemistryDetail detail = TeamChemistryCalculator.calculate(lineup);
                return Mono.just(ResponseEntity.ok((Object) detail));
            })
            .onErrorResume(IllegalArgumentException.class, ex ->
                Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()))))
            .onErrorResume(NotEnoughPlayersException.class, ex ->
                Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()))));
    }
}
