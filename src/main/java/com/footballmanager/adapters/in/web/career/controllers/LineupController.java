package com.footballmanager.adapters.in.web.career.controllers;

import com.footballmanager.adapters.in.web.career.lineup.dto.*;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.TacticalChemistry;
import com.footballmanager.domain.model.valueobject.TacticalChemistryCalculator;
import com.footballmanager.domain.model.valueobject.TeamChemistryCalculator;
import com.footballmanager.domain.model.valueobject.TeamRatingsCalculator;
import com.footballmanager.domain.port.in.lineup.LineupCommandUseCase;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import com.footballmanager.domain.port.in.lineup.LineupPlayerView;
import com.footballmanager.domain.port.in.lineup.LineupView;
import com.footballmanager.domain.port.in.lineup.LineupWarning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
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
@RequestMapping(value = "/api/v1/career/lineup", produces = "application/json;charset=UTF-8")
@RequiredArgsConstructor
public class LineupController {

    private final LineupCommandUseCase lineupCommandUseCase;
    private final LineupQueryUseCase lineupQueryUseCase;
    private final CareerSessionService careerSessionService;
    // ratings endpoint applies the new distance-aware effectiveness.
    private final FormationService formationService;
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
                return lineupCommandUseCase.autoSelectLineup(userId, request.formation())
                    .map(LineupController::toLineupDto);
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
                    toDomainSlots(request.slots()))
                    .map(LineupController::toLineupDto);
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
        return lineupQueryUseCase.getCurrentLineup(userId)
            .map(LineupController::toLineupDto);
    }

    /**
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
                Map<String, String> naturalByPlayer = new HashMap<>();
                for (String id : request.playerIds()) {
                    SessionPlayer p = allPlayers.get(id);
                    if (p == null) {
                        missing.add(id);
                    } else {
                        lineup.add(p);
                        if (p.getPosition() != null) {
                            naturalByPlayer.put(id, p.getPosition());
                        }
                    }
                }

                if (!missing.isEmpty()) {
                    // 404 con los ids faltantes — front puede log y mostrar fallback.
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of(
                            "error", "Some playerIds not found in career",
                            "missing", missing)));
                }

                // lineup.size() == 11 garantizado (11 ids válidos, request validó size 11).
                ChemistryDetail detail = TeamChemistryCalculator.calculate(lineup);
                if (request.slots() == null || request.slots().isEmpty()) {
                    return Mono.just(ResponseEntity.ok((Object) detail));
                }
                TacticalChemistry tacticalChemistry = null;
                Map<String, double[]> coordsBySubdivision =
                        formationService.getCoordsByFormation(request.formation());
                tacticalChemistry = TacticalChemistryCalculator.calculate(
                        toDomainSlots(request.slots()),
                        naturalByPlayer,
                        coordsBySubdivision);
                ChemistryBreakdownDTO breakdown = ChemistryBreakdownDTO.from(
                        detail,
                        request.slots(),
                        naturalByPlayer,
                        TacticalChemistryDTO.from(tacticalChemistry));
                return Mono.just(ResponseEntity.ok((Object) PreviewChemistryResponseDTO.from(
                        detail,
                        breakdown)));
            })
            .onErrorResume(IllegalArgumentException.class, ex ->
                Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()))))
            .onErrorResume(NotEnoughPlayersException.class, ex ->
                Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()))));
    }

    /**
     * (attack / midfield / defense) for an arbitrary lineup. The
     * frontend Team Stats panel calls this on every drag-drop (debounced
     * ~150ms) so the rating chips/bars reflect engine math without
     * requiring a save round-trip.
     *
     * <p>{@code POST /api/v1/career/lineup/preview-ratings}
     *
     * <p>Body: {@link PreviewRatingsRequest} with the formation label and
     * the current slot assignments (may be empty / partial while the
     * user is dragging).
     *
     * <p>Response: {@link PreviewRatingsResponse} with the three
     * modifier × 100 values.
     *
     * <p><b>Read-only:</b> nothing is persisted. The endpoint reads the
     * 11 SessionPlayers from the career's cache (same path
     * {@code /preview-chemistry} uses) to look up per-player attributes
     * for the rating formula. If a playerId is missing from the career
     * the rating for that slot falls back to median (70) stat values,
     * matching the engine's defensive fallback.
     *
     * <p><b>Performance:</b> the calculator is O(N log N) on top-7 sort
     * endpoint comfortably runs in
     * &lt;1ms on a hot path. No DB hits — career + squad live in Redis
     * cache.
     */
    @PostMapping("/preview-ratings")
    public Mono<ResponseEntity<?>> previewRatings(
            @RequestBody PreviewRatingsRequest request,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return careerSessionService.getCareerFromCache(userId)
                .<ResponseEntity<?>>flatMap(career -> {
                    Map<String, SessionPlayer> allPlayers = career.getSessionPlayers();
                    if (allPlayers == null || allPlayers.isEmpty()) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                                Map.of("error", "No players found in career",
                                        "userId", userId.toString())));
                    }

                    List<LineupSlotDTO> slots = (request.slots() == null) ? List.of() : request.slots();
                    Map<String, String> naturalByPlayer = new HashMap<>();
                    List<FormationEffectiveness.PlayerAttrDTO> attrsByPlayer = new ArrayList<>();
                    List<String> missing = new ArrayList<>();
                    for (LineupSlotDTO slot : slots) {
                        if (slot == null || slot.playerId() == null) continue;
                        SessionPlayer p = allPlayers.get(slot.playerId());
                        if (p == null) {
                            missing.add(slot.playerId());
                            continue;
                        }
                        if (p.getPosition() != null) {
                            naturalByPlayer.put(slot.playerId(), p.getPosition());
                        }
                        attrsByPlayer.add(new FormationEffectiveness.PlayerAttrDTO(
                                slot.playerId(),
                                p.getAttack(),
                                p.getDefense(),
                                p.getTechnique(),
                                p.getMentality()));
                    }

                    if (!missing.isEmpty()) {
                        // 404 with the ids we couldn't resolve — front can
                        // log + render fallback. Same defensive shape as
                        // /preview-chemistry.
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                                Map.of("error", "Some playerIds not found in career",
                                        "missing", missing)));
                    }

                    // the FormationService cache so the rating calculator
                    // can apply the distance-from-ideal penalty. Without
                    // this, fine-grained drag-and-drop on the field
                    // produces no rating change (the calculator falls
                    // back to the legacy zone-only math when coords are
                    // missing).
                    Map<String, double[]> coordsBySubdivision =
                            formationService.getCoordsByFormation(request.formation());

                    // Reuse FormationEffectiveness.from — it computes the
                    // three ratings (and only them; perPlayerEffectiveness
                    // and teamAverage are computed too but the response
                    // here only surfaces the ratings).
                    FormationEffectiveness fe = FormationEffectiveness.from(
                            toDomainSlots(slots),
                            naturalByPlayer,
                            request.formation(),
                            attrsByPlayer,
                            request.formation(),
                            coordsBySubdivision);
                    return Mono.just(ResponseEntity.ok((Object) new PreviewRatingsResponse(
                            fe.attackRating() != null ? fe.attackRating() : 100.0,
                            fe.midfieldRating() != null ? fe.midfieldRating() : 100.0,
                            fe.defenseRating() != null ? fe.defenseRating() : 100.0,
                            fe.inferredFormation(),
                            fe.perPlayerEffectiveness(),
                            fe.teamAverage())));
                })
                .onErrorResume(IllegalArgumentException.class, ex ->
                        Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()))))
                .onErrorResume(NotEnoughPlayersException.class, ex ->
                        Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()))));
    }

    private List<LineupSlot> toDomainSlots(List<LineupSlotDTO> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        return slots.stream()
                .filter(Objects::nonNull)
                .map(slot -> new LineupSlot(
                        slot.playerId(),
                        slot.subdivisionId(),
                        slot.customXPercent(),
                        slot.customYPercent()))
                .toList();
    }

    private static LineupDTO toLineupDto(LineupView view) {
        List<LineupSlotDTO> slots = view.slots().stream()
                .map(LineupController::toLineupSlotDto)
                .toList();
        Map<String, String> naturalByPlayer = new HashMap<>();
        for (LineupPlayerView player : view.players()) {
            if (player.playerId() != null && player.position() != null) {
                naturalByPlayer.put(player.playerId(), player.position());
            }
        }
        return new LineupDTO(
                view.formation(),
                view.players().stream().map(LineupController::toPlayerLineupDto).toList(),
                view.confirmed(),
                view.warnings().stream().map(LineupController::toLineupWarningDto).toList(),
                slots,
                view.chemistryScore(),
                ChemistryBreakdownDTO.from(
                        view.chemistryBreakdown(),
                        slots,
                        naturalByPlayer,
                        TacticalChemistryDTO.from(view.tacticalChemistry())),
                FormationEffectivenessDTO.from(view.formationEffectiveness()));
    }

    private static PlayerLineupDTO toPlayerLineupDto(LineupPlayerView player) {
        return new PlayerLineupDTO(
                player.playerId(),
                player.name(),
                player.position(),
                player.overall(),
                player.energy(),
                player.injured(),
                player.age(),
                player.yellowCards(),
                player.redCards(),
                player.suspended(),
                player.suspensionRemainingMatches());
    }

    private static LineupSlotDTO toLineupSlotDto(LineupSlot slot) {
        return new LineupSlotDTO(
                slot.playerId(),
                slot.subdivisionId(),
                slot.customXPercent(),
                slot.customYPercent());
    }

    private static LineupWarningDTO toLineupWarningDto(LineupWarning warning) {
        return new LineupWarningDTO(
                warning.code(),
                warning.message(),
                warning.severity(),
                warning.available(),
                warning.minimumRequired(),
                warning.target());
    }
}
