package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.ChemistryBreakdownDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationEffectivenessDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationPositionDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupWarningDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.lineup.LineupCommandUseCase;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.TeamChemistryCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación de UseCase para comandos del lineup.
 *
 * <p>V24D6U2: Supports short-handed lineups via manual-select / confirmLineup
 * (range {@code [MIN, MAX]}). See {@link LineupRules}.
 *
 * <p>V25D59-C19 P0: auto-select NO longer produces short-handed lineups.
 * It guarantees exactly 11 slots (GK + DEF + MID + ATT) by filling missing
 * formation-row slots with the best-OVR off-position players and attaching
 * a {@code LINEUP_OFF_POSITION_FILL} warning per affected row. If the squad
 * has fewer than {@link LineupRules#TARGET_LINEUP_PLAYERS} available players
 * the call throws {@link NotEnoughPlayersException} — silent short-handed
 * success is the bug this fix closes.
 *
 * <p>Manual select and confirmLineup still accept the {@code [MIN, MAX]}
 * range for backward compat with careers mid-rescue from short squads.
 */
@Service
@RequiredArgsConstructor
public class LineupCommandUseCaseImpl implements LineupCommandUseCase {

    private final CareerRepository careerRepository;
    private final LineupHelper lineupHelper;
    private final FormationService formationService;

    @Override
    public Mono<LineupDTO> autoSelectLineup(UUID userId, String formationCode) {
        Formation formation = Formation.fromString(formationCode);

        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> optionalCareer.isPresent()
                ? Mono.just(optionalCareer.get())
                : Mono.empty())
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                AutoSelectResult result = performAutoSelect(career, userTeamId, formation);
                List<SessionPlayer> lineup = result.lineup();
                List<LineupWarningDTO> warnings = result.warnings();

                List<String> lineupIds = lineup.stream()
                    .map(SessionPlayer::getSessionPlayerId)
                    .toList();
                career.getTeamStarting11().put(userTeamId, lineupIds);

                // MVP1-lineup-cancha-1.5: persist subdivision map so that
                // re-opening the modal restores exact slot assignments
                // (vs. role-match fallback that only fills GK + first 2 CB).
                // V25D61-C20.1 P0: pass isAutoSelect=true so the off-position
                // fallback fires (auto-select requires 11 slots).
                Map<String, String> slotMap = buildAutoSelectSlotMap(formation, lineup, true);
                // V25D60-C20 P0: defensive guard for auto-select. The earlier
                // lineup.size() check (TARGET_LINEUP_PLAYERS = 11) catches the
                // common "short squad" case, but if for any reason slotMap is
                // still incomplete (e.g. a future formation breaks the
                // formationDto.positions().size() == 11 invariant, or all
                // playerIds are null), fail loud instead of silently
                // persisting a partial slot map. Manual-select keeps the
                // best-effort behavior for short-handed rescues.
                if (slotMap.size() != LineupRules.TARGET_LINEUP_PLAYERS) {
                    throw new IllegalStateException(
                        "Auto-select slot assignment incomplete: " + slotMap.size()
                        + " / " + LineupRules.TARGET_LINEUP_PLAYERS
                        + " (formation: " + formation.getCode() + ", squad may be too small)"
                    );
                }
                if (slotMap.isEmpty()) {
                    career.getTeamStarting11Subdivision().remove(userTeamId);
                } else {
                    career.getTeamStarting11Subdivision().put(userTeamId, slotMap);
                }

                // MVP1-lineup-cancha-1.6: persist formation code so that
                // getCurrentLineup returns the actual formation the user
                // selected (not the one inferred from DEF/MID/ATT counts
                // of the lineup, which stays as the previous formation).
                career.getTeamStarting11Formation().put(userTeamId, formation.getCode());

                return careerRepository.save(career)
                    .thenReturn(buildLineupDTO(lineup, formation, warnings, slotMap));
            });
    }

    @Override
    public Mono<LineupDTO> manualSelectLineup(UUID userId, String formationCode, List<String> playerIds) {
        // Backward compat: legacy callers sin slots.
        return manualSelectLineupWithSlots(userId, formationCode, playerIds, List.of());
    }

    @Override
    public Mono<LineupDTO> manualSelectLineupWithSlots(UUID userId, String formationCode,
                                                      List<String> playerIds,
                                                      List<LineupSlotDTO> slots) {
        Formation formation = Formation.fromString(formationCode);

        if (playerIds.size() < LineupRules.MIN_AVAILABLE_PLAYERS) {
            return Mono.error(new NotEnoughPlayersException(
                "Minimum " + LineupRules.MIN_AVAILABLE_PLAYERS
                + " available players required, got " + playerIds.size()));
        }
        if (playerIds.size() > LineupRules.MAX_LINEUP_PLAYERS) {
            return Mono.error(new IllegalArgumentException(
                "Maximum " + LineupRules.MAX_LINEUP_PLAYERS
                + " players allowed, got " + playerIds.size()));
        }
        if (playerIds.stream().distinct().count() != playerIds.size()) {
            return Mono.error(new IllegalArgumentException("Cannot select same player twice"));
        }

        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> optionalCareer.isPresent()
                ? Mono.just(optionalCareer.get())
                : Mono.empty())
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                List<String> squadIds = career.getTeamManager().getTeamSquads().get(userTeamId);

                for (String playerId : playerIds) {
                    if (!squadIds.contains(playerId)) {
                        return Mono.error(new IllegalArgumentException(
                            "Player " + playerId + " not in your squad"));
                    }
                }

                List<SessionPlayer> selectedPlayers = playerIds.stream()
                    .map(id -> career.getSessionPlayers().get(id))
                    .filter(Objects::nonNull)
                    .toList();

                // Reject if selected players are not all available (injured/suspended/low energy)
                lineupHelper.validatePlayerFitness(selectedPlayers);

                // Compute warnings (no-GK, position-deficit informational)
                List<LineupWarningDTO> warnings = lineupHelper.detectShortHandedWarnings(selectedPlayers);
                if (selectedPlayers.size() < LineupRules.TARGET_LINEUP_PLAYERS) {
                    warnings = new ArrayList<>(warnings);
                    warnings.add(LineupWarningDTO.shortHanded(selectedPlayers.size()));
                }

                career.getTeamStarting11().put(userTeamId, playerIds);

                // MVP1-lineup-cancha-1.6: persist formation code so that
                // getCurrentLineup returns the actual formation the user
                // selected (same rationale as autoSelectLineup above).
                career.getTeamStarting11Formation().put(userTeamId, formation.getCode());

                // MVP1-lineup-cancha-1.6: persist subdivision map using
                // HELPER-BASED match (back is source-of-truth for slot
                // assignments). Front overrides apply on top for slots the
                // user assigned explicitly (manual drag-drop). Si el front
                // no envía slots, back completa los 11 slots vía helper.
                // V25D61-C20.1 P0: pass isAutoSelect=false so the off-position
                // fallback does NOT fire for short-handed manual-select
                // (prevents 7 players → 8 slots with a duplicated playerId).
                Map<String, String> slotMap = buildAutoSelectSlotMap(formation, selectedPlayers, false);
                if (slots != null && !slots.isEmpty()) {
                    // Override con lo que el front envió explícitamente
                    // (autoridad del front si el usuario asignó manualmente).
                    for (LineupSlotDTO slot : slots) {
                        if (slot.subdivisionId() == null || slot.subdivisionId().isBlank()) {
                            continue;
                        }
                        if (slot.playerId() == null || slot.playerId().isBlank()) {
                            continue;
                        }
                        if (!playerIds.contains(slot.playerId())) {
                            // Slot referencia un playerId no incluido en este lineup — ignorar.
                            continue;
                        }
                        // Si dos slots intentan usar el mismo subdivisionId, el último gana.
                        slotMap.put(slot.subdivisionId(), slot.playerId());
                    }
                }
                if (!slotMap.isEmpty()) {
                    career.getTeamStarting11Subdivision().put(userTeamId, slotMap);
                } else {
                    // Si HELPER-BASED no produjo nada (short-handed lineup),
                    // limpiamos el entry existente para no dejar datos stale.
                    career.getTeamStarting11Subdivision().remove(userTeamId);
                }

                return careerRepository.save(career)
                    .thenReturn(buildLineupDTO(selectedPlayers, formation, warnings, slotMap));
            });
    }

    @Override
    public Mono<Void> confirmLineup(UUID userId) {
        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> optionalCareer.isPresent()
                ? Mono.just(optionalCareer.get())
                : Mono.empty())
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                List<String> lineupIds = career.getTeamStarting11().get(userTeamId);

                if (lineupIds == null) {
                    return Mono.error(new NotEnoughPlayersException(
                        "No lineup selected. Minimum "
                        + LineupRules.MIN_AVAILABLE_PLAYERS + " players required."));
                }
                int size = lineupIds.size();
                if (size < LineupRules.MIN_AVAILABLE_PLAYERS) {
                    return Mono.error(new NotEnoughPlayersException(
                        "Lineup has only " + size + " players. Minimum "
                        + LineupRules.MIN_AVAILABLE_PLAYERS + " required."));
                }
                if (size > LineupRules.MAX_LINEUP_PLAYERS) {
                    return Mono.error(new IllegalArgumentException(
                        "Lineup has " + size + " players. Maximum "
                        + LineupRules.MAX_LINEUP_PLAYERS + " allowed."));
                }

                return careerRepository.save(career).then();
            });
    }

    private record AutoSelectResult(List<SessionPlayer> lineup, List<LineupWarningDTO> warnings) {}

    private AutoSelectResult performAutoSelect(CareerSave career, String teamId, Formation formation) {
        List<String> squadIds = career.getTeamManager().getTeamSquads().get(teamId);

        if (squadIds == null || squadIds.isEmpty()) {
            throw new NotEnoughPlayersException("No squad found for team: " + teamId);
        }

        List<SessionPlayer> availablePlayers = squadIds.stream()
            .map(id -> career.getSessionPlayers().get(id))
            .filter(Objects::nonNull)
            .filter(p -> p.getEnergy() > 20)
            .filter(this::isPlayerAvailable)
            .filter(p -> !Boolean.TRUE.equals(p.getSuspended()))
            .filter(p -> p.getSuspensionRemainingMatches() <= 0)
            .sorted(Comparator.comparing(SessionPlayer::calculateOverall).reversed())
            .toList();

        // V25D59-C19 P0: auto-select requires a full squad.
        // Below TARGET_LINEUP_PLAYERS (11) → throw NotEnoughPlayersException so the
        // controller returns 422 LINEUP_MINIMUM_PLAYERS_NOT_MET instead of persisting
        // a silently short-handed lineup (the C18b audit bug). Manual-select keeps the
        // [MIN, MAX] short-handed path for career rescue.
        if (availablePlayers.size() < LineupRules.TARGET_LINEUP_PLAYERS) {
            throw new NotEnoughPlayersException(
                "Auto-select requires " + LineupRules.TARGET_LINEUP_PLAYERS
                + " available players, got " + availablePlayers.size());
        }

        List<SessionPlayer> lineup = new ArrayList<>();
        List<LineupWarningDTO> warnings = new ArrayList<>();
        Set<String> alreadyTaken = new HashSet<>();

        // 1. GK — strict-match first; off-position fallback to best OVR if no
        // natural GK in the squad (e.g. a CDM filling GK). Attaches
        // LINEUP_NO_GOALKEEPER warning so the UI surfaces the tactical hit.
        SessionPlayer gk = availablePlayers.stream()
            .filter(p -> "GK".equals(p.getPosition()))
            .findFirst()
            .orElse(null);
        if (gk != null) {
            lineup.add(gk);
            alreadyTaken.add(gk.getSessionPlayerId());
        } else {
            SessionPlayer gkFallback = availablePlayers.stream()
                .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
                .findFirst()
                .orElseThrow(() -> new NotEnoughPlayersException(
                    "No available players for GK fallback (squad=" + availablePlayers.size() + ")"));
            lineup.add(gkFallback);
            alreadyTaken.add(gkFallback.getSessionPlayerId());
            warnings.add(LineupWarningDTO.noGoalkeeper(availablePlayers.size()));
        }

        // 2. DEF — best DEF-capable players first; any remaining DEF slots
        // are filled with the best-OVR remaining players (off-position).
        fillRow(availablePlayers, lineup, alreadyTaken, warnings,
            formation.getDefenders(), "DEF", lineupHelper::isDefender);

        // 3. MID — same off-position fallback pattern.
        fillRow(availablePlayers, lineup, alreadyTaken, warnings,
            formation.getMidfielders(), "MID", lineupHelper::isMidfielder);

        // 4. ATT — same off-position fallback pattern.
        fillRow(availablePlayers, lineup, alreadyTaken, warnings,
            formation.getAttackers(), "ATT", lineupHelper::isAttacker);

        // V25D59-C19 P0: validate the lineup reached exactly 11 slots before
        // persisting. Defensive — the algorithm above should always reach 11
        // for a squad of ≥11, but if a future formation breaks the invariant
        // (defenders + midfielders + attackers != 10) we fail loud instead of
        // silently persisting a malformed lineup.
        if (lineup.size() != LineupRules.TARGET_LINEUP_PLAYERS) {
            throw new NotEnoughPlayersException(
                "Auto-select produced " + lineup.size() + " players, expected "
                + LineupRules.TARGET_LINEUP_PLAYERS);
        }

        return new AutoSelectResult(lineup, warnings);
    }

    /**
     * V25D59-C19 P0: fill one formation row (DEF / MID / ATT) of {@code slotsNeeded}
     * slots with the best players available, preferring {@code positionMatcher}-compatible
     * players and falling back to the best-OVR remaining players (off-position) when
     * the squad lacks enough compatible players for the row. Adds a
     * {@link LineupWarningDTO#offPositionFill} warning when the fallback path is used.
     *
     * <p>Contract: appends to {@code lineup} in-place and updates {@code alreadyTaken}.
     * Assumes the caller has reserved the GK slot (slot 0) before calling.
     */
    private void fillRow(List<SessionPlayer> availablePlayers,
                         List<SessionPlayer> lineup,
                         Set<String> alreadyTaken,
                         List<LineupWarningDTO> warnings,
                         int slotsNeeded,
                         String positionGroup,
                         java.util.function.Predicate<String> positionMatcher) {
        if (slotsNeeded <= 0) {
            return;
        }

        // Phase 1: take up to slotsNeeded position-perfect players.
        List<SessionPlayer> perfect = availablePlayers.stream()
            .filter(p -> positionMatcher.test(p.getPosition()))
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .limit(slotsNeeded)
            .collect(Collectors.toList());
        lineup.addAll(perfect);
        perfect.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));

        // Phase 2: any remaining slots for this row → off-position fallback
        // (best OVR from remaining available players). The penalty is reflected
        // in formationEffectiveness (sprint C11a PositionEffectivenessCalculator),
        // and surfaced as a LINEUP_OFF_POSITION_FILL warning.
        int stillNeeded = slotsNeeded - perfect.size();
        if (stillNeeded > 0) {
            List<SessionPlayer> offPosFill = availablePlayers.stream()
                .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
                .limit(stillNeeded)
                .collect(Collectors.toList());
            lineup.addAll(offPosFill);
            offPosFill.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));
            long offPosCount = offPosFill.stream()
                .filter(p -> !positionMatcher.test(p.getPosition()))
                .count();
            if (offPosCount > 0) {
                warnings.add(LineupWarningDTO.offPositionFill(positionGroup, (int) offPosCount));
            }
        }
    }

    private LineupDTO buildLineupDTO(List<SessionPlayer> players, Formation formation,
                                      List<LineupWarningDTO> warnings,
                                      Map<String, String> slotMap) {
        List<PlayerLineupDTO> playerDTOs = players.stream()
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

        // V25D47 (Sprint C11a): build the slot DTOs and the tactical effectiveness
        // aggregate. Convert the subdivisionId → playerId map into the
        // LineupSlotDTO list (same shape used by LineupQueryUseCaseImpl), then
        // compute per-player effectiveness multipliers via
        // PositionEffectivenessCalculator.effectiveness(naturalPosition, slotCategory).
        //
        // V25D52 (Sprint C13b): LineupSlotDTO is record(playerId, subdivisionId)
        // — args MUST be (playerId, subdivisionId). slotMap is keyed by
        // subdivisionId with playerId values, so the constructor call is
        // (e.getValue(), e.getKey()). Prior to this fix the args were
        // swapped, which silently produced LineupSlotDTO(playerId="S22-1",
        // subdivisionId="def-1"). The downstream FormationEffectiveness.from()
        // then looked up naturalByPlayer.get("S22-1") (always null) and
        // categoryFor("def-1") (always null) → every effectiveness defaulted
        // to 1.0. Now the POST response matches the GET response shape.
        List<LineupSlotDTO> slots = (slotMap == null || slotMap.isEmpty())
                ? List.of()
                : slotMap.entrySet().stream()
                    .map(e -> new LineupSlotDTO(e.getValue(), e.getKey()))
                    .toList();
        Map<String, String> naturalByPlayer = new HashMap<>();
        for (SessionPlayer p : players) {
            if (p.getSessionPlayerId() != null && p.getPosition() != null) {
                naturalByPlayer.put(p.getSessionPlayerId(), p.getPosition());
            }
        }
        // V25D55 (Sprint C16): manual-select just persisted formation.getCode()
        // into CareerSave.teamStarting11Formation (line above). Pass it through
        // so the inferredFormation field matches the actual selected label
        // (e.g., "3-5-2-CDM") instead of collapsing to a 3-DIGIT triple.
        FormationEffectiveness formationEffectiveness =
                FormationEffectiveness.from(slots, naturalByPlayer, formation.getCode());

        // V25D41 (Sprint C6): compute team chemistry from the SessionPlayer list.
        // V25D43 (Sprint C8): calculate() now returns ChemistryDetail (score + breakdown).
        ChemistryDetail chemistryDetail = TeamChemistryCalculator.calculate(players);
        return new LineupDTO(formation.getCode(), playerDTOs, false, warnings, slots,
                chemistryDetail.score(),
                ChemistryBreakdownDTO.from(chemistryDetail),
                FormationEffectivenessDTO.from(formationEffectiveness));
    }

    private boolean isPlayerAvailable(SessionPlayer p) {
        if (Boolean.TRUE.equals(p.getInjured())) {
            return false;
        }
        if (p.getInjuryRemainingMatches() != null && p.getInjuryRemainingMatches() > 0) {
            return false;
        }
        return true;
    }

    /**
     * MVP1-lineup-cancha-1.6: Build the subdivision map using HELPER-BASED
     * role match — GK is exact, defensive roles (LB/CB/RB/LWB/RWB) match via
     * {@code lineupHelper.isDefender}, midfield roles (CDM/CM/CAM/LM/RM/LW/RW)
     * via {@code lineupHelper.isMidfielder}, and attacking roles (CF/ST)
     * via {@code lineupHelper.isAttacker}.
     *
     * <p>HELPER-BASED is a super-set of the EXACT-match used in 1.5: it
     * matches every player the EXACT-match would, plus players with
     * compatible-but-not-identical positions (e.g. a CB player filling a
     * LB slot — both are defenders per the helper). For a real-world squad
     * like Real Madrid with mixed positions (CB/LB/RB/CDM/CAM/LW/ST/RW),
     * EXACT match only filled 5-7 of the 11 slots; HELPER-BASED fills all 11.
     *
     * <p>Back is source-of-truth for slot assignments (F4 manual-select also
     * uses this method, with front overrides applied on top). The front's
     * re-open modal restores slots verbatim from the persisted subdivision
     * map (no role-match fallback), so back/front cannot diverge.
     *
     * <p>V25D61-C20.1 P0: the off-position fallback (V25D60-C20) is gated by
     * the {@code isAutoSelect} flag. Auto-select ({@code true}) requires the
     * slot map to cover every formation position (11 slots) so downstream
     * consumers (FormationEffectiveness, manual-select re-open) cannot recover
     * from a partial map. Manual-select ({@code false}) preserves the
     * short-handed contract — only helper-compatible assignments are made,
     * remaining slots are left empty, and no off-position fallback fires
     * (which would over-fill the map when {@code lineup.size() < formation
     * positions}, e.g. 7 players → 8 slots with a duplicated playerId).
     *
     * @param formation the formation whose positions drive the slot map
     * @param lineup the players available for assignment (already filtered
     *               by fitness for manual-select; full squad for auto-select)
     * @param isAutoSelect {@code true} for the auto-select path (requires
     *                     full coverage via off-position fallback);
     *                     {@code false} for the manual-select path (best-effort
     *                     helper match only)
     * @return subdivision → playerId map (may have fewer entries than
     *         formation positions when {@code isAutoSelect} is {@code false})
     */
    private Map<String, String> buildAutoSelectSlotMap(
            Formation formation,
            List<SessionPlayer> lineup,
            boolean isAutoSelect) {
        if (formationService == null) {
            return Map.of();
        }
        FormationDTO formationDto = formationService.getFormationByName(formation.getCode());
        if (formationDto == null || formationDto.positions() == null) {
            return Map.of();
        }
        Map<String, String> slotMap = new HashMap<>();
        Set<String> usedPlayerIds = new HashSet<>();
        for (FormationPositionDTO pos : formationDto.positions()) {
            String role = pos.role();
            String subdivisionId = pos.subdivisionId();
            if (role == null || subdivisionId == null || subdivisionId.isBlank()) {
                continue;
            }
            boolean assigned = false;
            // Phase 1: helper-based match (natural / compatible position).
            for (SessionPlayer player : lineup) {
                String playerId = player.getSessionPlayerId();
                if (playerId == null || usedPlayerIds.contains(playerId)) {
                    continue;
                }
                boolean matches = switch (role) {
                    case "GK" -> "GK".equals(player.getPosition());
                    case "LB", "CB", "RB", "LWB", "RWB" -> lineupHelper.isDefender(player.getPosition());
                    case "CDM", "CM", "CAM", "LM", "RM", "LW", "RW" -> lineupHelper.isMidfielder(player.getPosition());
                    case "CF", "ST" -> lineupHelper.isAttacker(player.getPosition());
                    default -> false;
                };
                if (matches) {
                    slotMap.put(subdivisionId, playerId);
                    usedPlayerIds.add(playerId);
                    assigned = true;
                    break;
                }
            }
            // V25D60-C20 P0 + V25D61-C20.1 P0: off-position fallback. If no
            // helper-compatible player was found for this slot, take the next
            // unused player from the lineup (any position). The effectiveness
            // penalty is surfaced downstream by PositionEffectivenessCalculator
            // (sprint C11a). Without this fallback the slot map can end up with
            // fewer entries than formation positions (e.g. squad without natural
            // DEF → DEF slots unassigned → only 7 of 11 subdivision entries
            // persisted), which downstream consumers (FormationEffectiveness,
            // manual-select re-open) cannot recover from.
            //
            // V25D61-C20.1 P0: the fallback is GATED by isAutoSelect. For
            // auto-select the fallback is required (caller fails loud via
            // IllegalStateException if slotMap.size() != 11). For manual-select
            // short-handed, the fallback is SKIPPED — otherwise it would
            // over-fill the map when lineup.size() < formation.positions (e.g.
            // 7 players + 4-4-2 → 8 slots with a duplicated playerId), breaking
            // the [MIN, MAX] contract.
            if (!assigned && isAutoSelect) {
                for (SessionPlayer player : lineup) {
                    String playerId = player.getSessionPlayerId();
                    if (playerId == null || usedPlayerIds.contains(playerId)) {
                        continue;
                    }
                    slotMap.put(subdivisionId, playerId);
                    usedPlayerIds.add(playerId);
                    break;
                }
            }
        }
        return slotMap;
    }
}
