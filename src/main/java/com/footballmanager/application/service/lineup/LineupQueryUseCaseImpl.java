package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.ChemistryBreakdownDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationEffectivenessDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupWarningDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.FormationInferer;
import com.footballmanager.domain.model.valueobject.TeamChemistryCalculator;
import com.footballmanager.domain.port.in.lineup.LineupQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
    // V25D99.16-BACK: resolved per-subdivision xPct/yPct so the team
    // ratings use the new distance-aware effectiveness. Injected via
    // @RequiredArgsConstructor.
    private final FormationService formationService;

    // V25D99.20.1: nullable injection so /current uses the in-memory cache
    // layer (ConcurrentHashMap in CareerSessionService) when Spring has
    // wired the dependency. Tests construct this class with the 3-arg
    // constructor (no Spring context), so the field stays null and the
    // legacy direct-repo path is exercised. With it, the read path
    // matches the write path (/auto-select, /manual-select, /confirm,
    // /preview-chemistry, /preview-ratings) which already use
    // careerSessionService.getCareerFromCache. Pre-fix: /current hit
    // Redis directly and returned 200 + empty body when the Redis key was
    // temporarily missing (TTL race, post-restart before first warming),
    // while every other endpoint kept serving from cache.
    @Autowired(required = false)
    @SuppressWarnings("PMD.UnusedPrivateField")
    private CareerSessionService careerSessionService;

    @Override
    public Mono<LineupDTO> getCurrentLineup(UUID userId) {
        Mono<CareerSave> careerMono;
        if (careerSessionService != null) {
            careerMono = careerSessionService.getCareerFromCache(userId);
        } else {
            // Legacy / test path: direct Redis read.
            careerMono = careerRepository.findById(userId.toString())
                .flatMap(optionalCareer -> optionalCareer.isPresent()
                    ? Mono.just(optionalCareer.get())
                    : Mono.empty());
        }
        return careerMono.map(this::buildLineupDTO);
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
            // V25D75-C40 C1: filter suspended players from the returned
            // lineup. The persisted state may include players that became
            // suspended mid-match (red card) AFTER the lineup was set —
            // we don't auto-cleanup the persisted state (other code paths
            // may rely on it), but the API response should not surface a
            // suspended player as part of the "current starting XI".
            // Mirrors the auto-select filter (LineupCommandUseCaseImpl
            // line ~266) and the validatePlayerFitness gate.
            .filter(p -> !Boolean.TRUE.equals(p.getSuspended()))
            .filter(p -> p.getSuspensionRemainingMatches() == null
                || p.getSuspensionRemainingMatches() <= 0)
            .toList();

        // MVP1-lineup-cancha-1.6: leer formación persistida con fallback a
        // inferFormation para saves viejos que no tienen teamStarting11Formation.
        String formationCode = readPersistedFormation(career, userTeamId, lineup);

        // V25D99.16-BACK: lookup per-subdivision coords from the
        // FormationService cache so /current responses include the new
        // subdivision-aware team ratings (manual-select persistence
        // already wired through CommandUseCaseImpl; this is the read
        // path that surfaces ratings on re-open).
        Map<String, double[]> coordsBySubdivision =
                formationService.getCoordsByFormation(formationCode);

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
        // V25D99.15-BACK: per-player attribute DTOs for the engine
        // rating computation. Without them, ratings default to the
        // formation baseline (4-4-2 = 100/100/100).
        List<FormationEffectiveness.PlayerAttrDTO> attrsByPlayer = new ArrayList<>();
        for (SessionPlayer p : lineup) {
            if (p.getSessionPlayerId() != null) {
                attrsByPlayer.add(new FormationEffectiveness.PlayerAttrDTO(
                        p.getSessionPlayerId(),
                        p.getAttack(),
                        p.getDefense(),
                        p.getTechnique(),
                        p.getMentality()));
            }
        }
        // V25D55 (Sprint C16): forward the persisted formation so the
        // inferredFormation field reflects the manager's selection (e.g.,
        // "3-5-2-CDM") instead of collapsing to the slot-count triple.
        String persistedFormationCode = career.getTeamStarting11Formation() == null
                ? null
                : career.getTeamStarting11Formation().get(userTeamId);
        FormationEffectiveness formationEffectiveness =
                FormationEffectiveness.from(
                        slots,
                        naturalByPlayer,
                        persistedFormationCode,
                        attrsByPlayer,
                        persistedFormationCode,
                        coordsBySubdivision);

        // V25D65-C25 P0: compute warnings from persisted state (slots + lineup).
        // Pre-C25 bug: warnings=List.of() here caused the banner to disappear
        // on reload (only POST /manual-select and /auto-select returned warnings).
        // Now warnings persist with the lineup (recomputed each /current call).
        List<LineupWarningDTO> warnings = computePersistedWarnings(
                lineup, slots, formationEffectiveness);

        return new LineupDTO(formationCode, playerDTOs, true, warnings, slots,
                chemistryDetail.score(),
                // V25D99.19-BACK (BUG-1 fix): pass slots + naturalByPlayer so
                // the ChemistryBreakdownDTO can pad empty PositionGroups with
                // slot-category fallback entries (e.g. lineup with legacy/zero
                // skill data where the skill-weight grouping yields empty
                // groups but the lineup has assigned slots). Pre-fix
                // Ivan saw `positionGroups: { GK: [], DEF: [], MID: [], ATT: [] }`
                // + coveragePercentage 0 rendered as the "0% coverage" UX
                // gap. The single-arg `from(detail)` remains available for
                // /preview-chemistry callers that have no slot context.
                ChemistryBreakdownDTO.from(chemistryDetail, slots, naturalByPlayer),
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
        //
        // V25D99.16-BACK: threshold relaxed from `< 1.0` to `< 0.85`.
        // Reason: SubdivisionEffectivenessCalculator now factors in
        // distance-from-ideal so a CB placed at the LB/RB wing DEF slot
        // drops to ~0.85 effectiveness (was 1.0 pre-V25D99.16 — pure
        // zone table). That drop is meaningful for the panel ratings
        // (so fine-grained drag-and-drop is visible) but a CB playing
        // LB is still a coherent defensive assignment, NOT an
        // off-position warning. The threshold now matches the engine's
        // intent: fire only for SIGNIFICANT off-position (cross-zone:
        // CB→MID, MID→ATT, etc. all score < 0.85 in the zone table).
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
                if (eff != null && eff < 0.85) {
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
        // V25D99.20.2-BACK: use the typed slot getter so we preserve
        // customXPercent / customYPercent on the LineupSlotDTOs. The
        // legacy String-only getter would discard these overrides and the
        // FormationEffectiveness calculator downstream would silently
        // fall back to canonical subdivision coords (no penalty for
        // free-positioned players).
        Map<String, Map<String, LineupSlotDTO>> allSlots = career.getTeamStarting11SubdivisionSlots();
        if (allSlots == null) {
            return List.of();
        }
        Map<String, LineupSlotDTO> teamSlots = allSlots.get(userTeamId);
        if (teamSlots == null || teamSlots.isEmpty()) {
            return List.of();
        }

        List<LineupSlotDTO> result = new ArrayList<>(teamSlots.size());
        for (Map.Entry<String, LineupSlotDTO> entry : teamSlots.entrySet()) {
            LineupSlotDTO inner = entry.getValue();
            // Outer key + inner subdivisionId should agree for fresh
            // writes. If they differ (e.g. legacy wrapped value with
            // null inner subdivisionId), prefer the inner when set, else
            // fall back to the outer key. Same fallback applies to
            // customX/Y: a null inner means canonical coords (the
            // FormationEffectiveness.from() handles that).
            String subdivisionId = inner.subdivisionId() != null
                    ? inner.subdivisionId()
                    : entry.getKey();
            result.add(new LineupSlotDTO(
                    inner.playerId(),
                    subdivisionId,
                    inner.customXPercent(),
                    inner.customYPercent()));
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
