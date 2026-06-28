package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.ChemistryBreakdownDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationEffectivenessDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupWarningDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.FormationInferer;
import com.footballmanager.domain.model.valueobject.TeamChemistryCalculator;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementación de UseCase para consultas del lineup.
 *
 * <p>MVP1-lineup-cancha-1: si el {@code CareerSave.teamStarting11Subdivision}
 * tiene slots persistidos para el team, los incluye en la respuesta.
 * Si está vacío o ausente, retorna {@code slots=[]} (backward compat — el front
 * infiere los slots del role del jugador).
 */
@Service
@RequiredArgsConstructor
public class LineupQueryUseCaseImpl implements LineupQueryUseCase {

    private final CareerRepository careerRepository;
    private final LineupHelper lineupHelper;

    @Override
    public Mono<LineupDTO> getCurrentLineup(UUID userId) {
        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> optionalCareer.isPresent()
                ? Mono.just(optionalCareer.get())
                : Mono.empty())
            .map(this::buildLineupDTO);
    }

    private LineupDTO buildLineupDTO(CareerSave career) {
        String userTeamId = career.getUserSessionTeamId();
        List<String> lineupIds = career.getTeamStarting11().get(userTeamId);

        if (lineupIds == null || lineupIds.isEmpty()) {
            // V25D41 (Sprint C6): empty lineup → chemistry = 0 (no lineup, no chemistry).
            // V25D43 (Sprint C8): empty breakdown (4 groups, all empty) for shape stability.
            // V25D47 (Sprint C11a): empty formation effectiveness (default formation,
            // empty per-player map, teamAverage=1.0).
            return new LineupDTO(null, Collections.emptyList(), false, List.of(), List.of(), 0,
                    ChemistryBreakdownDTO.empty(),
                    FormationEffectivenessDTO.empty());
        }

        // V25D65-C25 P0: el lineup vacío no genera warnings (no hay jugadores para
        // evaluar short-handed / no-GK / off-position). Matchea el comportamiento
        // pre-C25 donde warnings=List.of() en este path.

        List<SessionPlayer> lineup = lineupIds.stream()
            .map(id -> career.getSessionPlayers().get(id))
            .filter(Objects::nonNull)
            .toList();

        // MVP1-lineup-cancha-1.6: leer formación persistida con fallback a
        // inferFormation para saves viejos que no tienen teamStarting11Formation.
        String formationCode = readPersistedFormation(career, userTeamId, lineup);

        List<PlayerLineupDTO> playerDTOs = lineup.stream()
            .map(p -> new PlayerLineupDTO(
                p.getSessionPlayerId(),
                p.getName(),
                p.getPosition(),
                p.calculateOverall(),
                p.getEnergy(),
                p.getInjured(),
                p.getAge(),
                p.getYellowCards(),
                p.getRedCards(),
                p.getSuspended(),
                p.getSuspensionRemainingMatches()
            ))
            .toList();

        List<LineupSlotDTO> slots = buildSlotsFromSubdivisionMap(career, userTeamId);

        // V25D41 (Sprint C6): compute team chemistry from the actual SessionPlayer
        // objects (we have the lineup List<SessionPlayer> here, not just the DTOs).
        // V25D43 (Sprint C8): calculate() now returns ChemistryDetail (score + breakdown).
        ChemistryDetail chemistryDetail = TeamChemistryCalculator.calculate(lineup);

        // V25D47 (Sprint C11a): formation effectiveness — inferred formation label
        // + per-player effectiveness multipliers (natural position vs slot category).
        // For empty/malformed slots → defaults to "4-4-2" + empty map + 1.0 team avg
        // (backward compat with lineups persisted before subdivisionId mapping).
        Map<String, String> naturalByPlayer = new HashMap<>();
        for (SessionPlayer p : lineup) {
            if (p.getSessionPlayerId() != null && p.getPosition() != null) {
                naturalByPlayer.put(p.getSessionPlayerId(), p.getPosition());
            }
        }
        // V25D55 (Sprint C16): forward the persisted formation so the
        // inferredFormation field reflects the manager's selection (e.g.,
        // "3-5-2-CDM") instead of collapsing to the slot-count triple.
        String persistedFormationCode = career.getTeamStarting11Formation() == null
                ? null
                : career.getTeamStarting11Formation().get(userTeamId);
        FormationEffectiveness formationEffectiveness =
                FormationEffectiveness.from(slots, naturalByPlayer, persistedFormationCode);

        // V25D65-C25 P0: compute warnings from persisted state (slots + lineup).
        // Pre-C25 bug: warnings=List.of() here caused the banner to disappear
        // on reload (only POST /manual-select and /auto-select returned warnings).
        // Now warnings persist with the lineup (recomputed each /current call).
        List<LineupWarningDTO> warnings = computePersistedWarnings(
                lineup, slots, formationEffectiveness);

        return new LineupDTO(formationCode, playerDTOs, true, warnings, slots,
                chemistryDetail.score(),
                ChemistryBreakdownDTO.from(chemistryDetail),
                FormationEffectivenessDTO.from(formationEffectiveness));
    }

    /**
     * V25D65-C25 P0: compute warnings for a persisted lineup from its slots
     * + players + effectiveness data. Mirrors what the command path
     * ({@code LineupCommandUseCaseImpl}) computes during armar, but for the
     * read path ({@code GET /career/lineup/current}) which previously
     * returned {@code warnings=List.of()}.
     *
     * <p>Three warning types computed here:
     * <ul>
     *   <li>{@code LINEUP_NO_GOALKEEPER} — via {@link LineupHelper#detectShortHandedWarnings}
     *       when no player in the lineup has natural position GK.</li>
     *   <li>{@code LINEUP_SHORT_HANDED} — when lineup.size() ∈ [7, 11) (manual-select
     *       short-handed mode). Adds even if the lineup passed the front-end
     *       validation; persistence shows the actual state.</li>
     *   <li>{@code LINEUP_OFF_POSITION_FILL} — counts subdivisionIds where
     *       {@code formationEffectiveness.perPlayerEffectiveness.get(subd) < 1.0}
     *       per category (GK/DEF/MID/ATT) via {@link FormationInferer#categoryFor}.
     *       Emits one warning per non-zero category.</li>
     * </ul>
     */
    private List<LineupWarningDTO> computePersistedWarnings(
            List<SessionPlayer> lineup,
            List<LineupSlotDTO> slots,
            FormationEffectiveness formationEffectiveness) {

        // Start with the helper's no-GK detection (covers lineup-null edge case).
        List<LineupWarningDTO> warnings = new ArrayList<>(
                lineupHelper.detectShortHandedWarnings(lineup));

        // Short-handed (manual-select mode allows 7-10 players).
        if (lineup.size() >= LineupRules.MIN_AVAILABLE_PLAYERS
                && lineup.size() < LineupRules.TARGET_LINEUP_PLAYERS) {
            warnings.add(LineupWarningDTO.shortHanded(lineup.size()));
        }

        // Off-position fill (only if effectiveness data is available — empty
        // slots map → no off-position data → no warning).
        if (slots != null && !slots.isEmpty()
                && formationEffectiveness != null
                && formationEffectiveness.perPlayerEffectiveness() != null
                && !formationEffectiveness.perPlayerEffectiveness().isEmpty()) {

            Map<String, Double> perPlayer = formationEffectiveness.perPlayerEffectiveness();
            // category (GK/DEF/MID/ATT) → count of off-position slots in that row.
            Map<String, Integer> offPositionCountByGroup = new HashMap<>();
            for (LineupSlotDTO slot : slots) {
                if (slot == null || slot.subdivisionId() == null) continue;
                Double eff = perPlayer.get(slot.subdivisionId());
                if (eff != null && eff < 1.0) {
                    String group = FormationInferer.categoryFor(slot.subdivisionId());
                    if (group != null) {
                        offPositionCountByGroup.merge(group, 1, Integer::sum);
                    }
                }
            }
            // Emit one warning per category that has off-position slots.
            // Iteration order: GK first, then DEF, MID, ATT (stable for tests).
            for (String group : List.of("GK", "DEF", "MID", "ATT")) {
                Integer count = offPositionCountByGroup.get(group);
                if (count != null && count > 0) {
                    warnings.add(LineupWarningDTO.offPositionFill(group, count));
                }
            }
        }

        return warnings;
    }

    private List<LineupSlotDTO> buildSlotsFromSubdivisionMap(CareerSave career, String userTeamId) {
        Map<String, Map<String, String>> allSlots = career.getTeamStarting11Subdivision();
        if (allSlots == null) {
            return List.of();
        }
        Map<String, String> teamSlots = allSlots.get(userTeamId);
        if (teamSlots == null || teamSlots.isEmpty()) {
            return List.of();
        }

        List<LineupSlotDTO> result = new ArrayList<>(teamSlots.size());
        for (Map.Entry<String, String> entry : teamSlots.entrySet()) {
            result.add(new LineupSlotDTO(entry.getValue(), entry.getKey()));
        }
        return result;
    }

    /**
     * MVP1-lineup-cancha-1.6: lee la formación persistida para el team.
     * Si el save es viejo (no tiene teamStarting11Formation, o el team no
     * tiene entry) → fallback a {@code lineupHelper.inferFormation(lineup)}.
     * Esto preserva el comportamiento de 1.5 y anteriores para saves previos
     * sin requerir migración explícita.
     */
    private String readPersistedFormation(CareerSave career, String userTeamId, List<SessionPlayer> lineup) {
        Map<String, String> formationMap = career.getTeamStarting11Formation();
        String persisted = (formationMap != null) ? formationMap.get(userTeamId) : null;
        if (persisted != null && !persisted.isBlank()) {
            return persisted;
        }
        // Backward compat: careerSave sin teamStarting11Formation (saves viejos
        // de sprints 1.5 o anteriores) → inferir del role distribution.
        return lineupHelper.inferFormation(lineup);
    }
}
