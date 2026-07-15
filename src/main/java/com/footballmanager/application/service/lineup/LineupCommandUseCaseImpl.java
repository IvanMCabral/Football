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
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
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
 *
 * <p>V25D78-C43 P0 (formation persistence fix): every save goes through
 * {@link CareerSessionService#saveCareer(CareerSave)} (which atomically
 * persists to Redis AND updates the in-memory {@code careerCache}) instead
 * of the raw {@code careerRepository.save}. Pre-fix, the orchestrator's
 * {@code getCareerFromCache} could return a stale pre-lineup object and
 * the next {@code saveCareer} call would overwrite Redis with the stale
 * state — wiping the lineup (the "0/11 players + formation null after
 * Confirmar y Jugar" smoke symptom). Reads also go through the session
 * service so the cache is populated on the read path; subsequent
 * orchestrator reads then see the fresh lineup.
 */
@Service
@RequiredArgsConstructor
public class LineupCommandUseCaseImpl implements LineupCommandUseCase {

    private final CareerSessionService careerSessionService;
    private final LineupHelper lineupHelper;
    private final FormationService formationService;

    @Override
    public Mono<LineupDTO> autoSelectLineup(UUID userId, String formationCode) {
        Formation formation = Formation.fromString(formationCode);

        // V25D78-C43 P0: read directly from Redis (NOT cache) to bypass any stale
        // cached CareerSave. Saves go through careerSessionService.saveCareer which
        // updates the cache atomically (saves to Redis then puts the new object in
        // the in-memory cache) — without this, the next read from
        // MatchSimulationOrchestrator would return a stale pre-lineup object and
        // overwrite Redis, losing the lineup (the "0/11 after continue-season" bug).
        return careerSessionService.continueCareer(userId)
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
                // V25D99.20.2-BACK: buildAutoSelectSlotMap now returns
                // Map<String, LineupSlotDTO> (subdivisionId → LineupSlotDTO
                // with customX/Y=null because auto-select is canonical). The
                // front's free-positioning overrides only arrive via manual-select.
                Map<String, LineupSlotDTO> slotMap = buildAutoSelectSlotMap(formation, lineup, true);
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
                // V25D99.20.3.1-BACK BUG-2 gap fix: my V25D99.20.3 fix
                // operated on a NEW typed map returned by
                // getTeamStarting11SubdivisionSlots(), then wrote it back
                // via setTeamStarting11SubdivisionSlots(). The unit test
                // passed (mocked same in-memory object). The runtime
                // FAILED because between unit and runtime, the CareerSave
                // is JSON-serialized to Redis and deserialized back.
                // Jackson's Map<String, Object> field deserializes the
                // inner values as raw LinkedHashMap (not LineupSlotDTO),
                // so the runtime's typed getter wraps them but the legacy
                // getter (which drove the runtime check) skips them
                // (instanceof String fails on LinkedHashMap).
                //
                // To eliminate the gap, operate directly on the RAW
                // field via the dedicated clear-and-put helper. This
                // bypasses the typed/raw conversion round-trip and
                // guarantees the raw field (and therefore the JSON
                // serialization) ends up with exactly the slotMap we
                // want, with no stale keys surviving the cycle.
                career.replaceTeamStarting11SubdivisionRaw(userTeamId, slotMap);

                // MVP1-lineup-cancha-1.6: persist formation code so that
                // getCurrentLineup returns the actual formation the user
                // selected (not the one inferred from DEF/MID/ATT counts
                // of the lineup, which stays as the previous formation).
                career.getTeamStarting11Formation().put(userTeamId, formation.getCode());

                return careerSessionService.saveCareer(career)
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

        return careerSessionService.continueCareer(userId)
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
                // V25D99.20.2-BACK: returns Map<String, LineupSlotDTO> so the
                // front's customXPercent / customYPercent override coords
                // (V25D98 free-positioning) survive the round-trip. Pre-fix,
                // the String-only shape discarded these coords and the
                // SubdivisionEffectivenessCalculator always saw canonical
                // coords (causing 1px-drag sensitivity = no observable
                // penalty because the back's penalty was always 0).
                Map<String, LineupSlotDTO> slotMap = buildAutoSelectSlotMap(formation, selectedPlayers, false);
                if (slots != null && !slots.isEmpty()) {
                    // Override con lo que el front envió explícitamente
                    // (autoridad del front si el usuario asignó manualmente).
                    // V25D99.20.2-BACK: keep the front's customXPercent and
                    // customYPercent so the engine's distance-from-ideal
                    // penalty reflects the actual drop point.
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
                        slotMap.put(slot.subdivisionId(), slot);
                    }
                }
                // V25D99.20.3.1-BACK BUG-2 gap: same root cause as autoSelectLineup.
                // Use the dedicated clear-and-put helper on the raw field
                // to bypass the typed/raw round-trip and guarantee the
                // JSON serialization ends up with exactly the slotMap.
                career.replaceTeamStarting11SubdivisionRaw(userTeamId, slotMap);

                return careerSessionService.saveCareer(career)
                    .thenReturn(buildLineupDTO(selectedPlayers, formation, warnings, slotMap));
            });
    }

    @Override
    public Mono<Void> confirmLineup(UUID userId) {
        return careerSessionService.continueCareer(userId)
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

                return careerSessionService.saveCareer(career).then();
            });
    }

    private record AutoSelectResult(List<SessionPlayer> lineup, List<LineupWarningDTO> warnings) {}
    private record OutfieldRoleNeeds(int defenders, int midfielders, int attackers) {}

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
        // V25D99.20.7-BACK: derive needs from the concrete visual slots.
        // This keeps LWB/RWB defensive and LW/RW attacking instead of relying
        // on coarse enum counts that collapse wingback/winger variants.
        OutfieldRoleNeeds roleNeeds = getOutfieldRoleNeeds(formation);
        boolean hasWideMidfieldSlots = formationHasAnyRole(formation, Set.of("LM", "RM", "LWB", "RWB"));
        boolean hasWideAttackingSlots = formationHasAnyRole(formation, Set.of("LW", "RW"));

        fillRow(availablePlayers, lineup, alreadyTaken, warnings,
            roleNeeds.defenders(), "DEF", lineupHelper::isDefender);

        // 3. MID — same off-position fallback pattern.
        fillRow(availablePlayers, lineup, alreadyTaken, warnings,
            roleNeeds.midfielders(), "MID",
            playerPosition -> isAutoSelectMidfieldCandidate(playerPosition, hasWideMidfieldSlots));

        // 4. ATT — same off-position fallback pattern.
        if (hasWideAttackingSlots) {
            fillAttackingRowWithWideSlotPreference(formation, availablePlayers, lineup, alreadyTaken, warnings,
                roleNeeds.attackers());
        } else {
            fillRow(availablePlayers, lineup, alreadyTaken, warnings,
                roleNeeds.attackers(), "ATT",
                playerPosition -> isAutoSelectAttackingCandidate(playerPosition, false));
        }

        includeSpecificRoleIfNeeded(formation, availablePlayers, lineup, alreadyTaken, "CAM");

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

    private void includeSpecificRoleIfNeeded(
            Formation formation,
            List<SessionPlayer> availablePlayers,
            List<SessionPlayer> lineup,
            Set<String> alreadyTaken,
            String role) {
        if (!formationHasRole(formation, role)) {
            return;
        }
        boolean alreadyCovered = lineup.stream()
            .anyMatch(player -> isSpecificNaturalRoleCover(role, player.getPosition()));
        if (alreadyCovered) {
            return;
        }
        SessionPlayer bestNatural = availablePlayers.stream()
            .filter(player -> player.getSessionPlayerId() != null)
            .filter(player -> !alreadyTaken.contains(player.getSessionPlayerId()))
            .filter(player -> isSpecificNaturalRoleCover(role, player.getPosition()))
            .findFirst()
            .orElse(null);
        if (bestNatural == null) {
            return;
        }
        for (int i = lineup.size() - 1; i >= 0; i--) {
            SessionPlayer selected = lineup.get(i);
            if (selected.getSessionPlayerId() == null || "GK".equals(selected.getPosition())) {
                continue;
            }
            if (isCentralForwardPosition(selected.getPosition()) || lineupHelper.isDefender(selected.getPosition())) {
                continue;
            }
            if (roleAwareSlotMatch(role, selected.getPosition())) {
                continue;
            }
            alreadyTaken.remove(selected.getSessionPlayerId());
            lineup.set(i, bestNatural);
            alreadyTaken.add(bestNatural.getSessionPlayerId());
            return;
        }
    }

    private boolean formationHasRole(Formation formation, String role) {
        if (formationService == null || formation == null || role == null) {
            return false;
        }
        FormationDTO formationDto = formationService.getFormationByName(formation.getCode());
        return formationDto != null
            && formationDto.positions() != null
            && formationDto.positions().stream().anyMatch(pos -> role.equals(pos.role()));
    }

    private boolean formationHasAnyRole(Formation formation, Set<String> roles) {
        if (formationService == null || formation == null || roles == null || roles.isEmpty()) {
            return false;
        }
        FormationDTO formationDto = formationService.getFormationByName(formation.getCode());
        return formationDto != null
            && formationDto.positions() != null
            && formationDto.positions().stream().anyMatch(pos -> roles.contains(pos.role()));
    }

    private boolean isSpecificNaturalRoleCover(String role, String playerPosition) {
        if (role == null || playerPosition == null) {
            return false;
        }
        String r = role.toUpperCase();
        String p = playerPosition.toUpperCase();
        if (r.equals(p)) {
            return true;
        }
        if ("CAM".equals(r)) {
            return "AM".equals(p);
        }
        return roleAwareSlotMatch(role, playerPosition);
    }

    private OutfieldRoleNeeds getOutfieldRoleNeeds(Formation formation) {
        OutfieldRoleNeeds fallback = new OutfieldRoleNeeds(
                formation.getDefenders(),
                formation.getMidfielders(),
                formation.getAttackers());
        if (formationService == null || formation == null) {
            return fallback;
        }
        FormationDTO formationDto = formationService.getFormationByName(formation.getCode());
        if (formationDto == null || formationDto.positions() == null) {
            return fallback;
        }

        int defenders = 0;
        int midfielders = 0;
        int attackers = 0;
        for (FormationPositionDTO pos : formationDto.positions()) {
            String role = pos.role();
            if (isDefensiveSlotRole(role)) {
                defenders++;
            } else if (isMidfieldSlotRole(role)) {
                midfielders++;
            } else if (isAttackingSlotRole(role)) {
                attackers++;
            }
        }

        if (defenders + midfielders + attackers != LineupRules.TARGET_LINEUP_PLAYERS - 1) {
            return fallback;
        }
        return new OutfieldRoleNeeds(defenders, midfielders, attackers);
    }

    private boolean isDefensiveSlotRole(String role) {
        return switch (role) {
            case "LB", "CB", "RB", "LWB", "RWB" -> true;
            default -> false;
        };
    }

    private boolean isMidfieldSlotRole(String role) {
        return switch (role) {
            case "CDM", "CM", "CAM", "LM", "RM" -> true;
            default -> false;
        };
    }

    private boolean isAttackingSlotRole(String role) {
        return switch (role) {
            case "LW", "RW", "CF", "ST" -> true;
            default -> false;
        };
    }

    private boolean isAutoSelectMidfieldCandidate(String playerPosition, boolean formationHasWideMidfieldSlots) {
        return lineupHelper.isMidfielder(playerPosition)
            || (formationHasWideMidfieldSlots && isGenericWingerPosition(playerPosition));
    }

    private boolean isAutoSelectAttackingCandidate(String playerPosition, boolean formationHasWideAttackingSlots) {
        return lineupHelper.isAttacker(playerPosition)
            || (formationHasWideAttackingSlots && isGenericWingerPosition(playerPosition));
    }

    private boolean isGenericWingerPosition(String playerPosition) {
        return playerPosition != null && "WINGER".equalsIgnoreCase(playerPosition);
    }

    private void fillAttackingRowWithWideSlotPreference(
            Formation formation,
            List<SessionPlayer> availablePlayers,
            List<SessionPlayer> lineup,
            Set<String> alreadyTaken,
            List<LineupWarningDTO> warnings,
            int slotsNeeded) {
        if (slotsNeeded <= 0) {
            return;
        }

        int wideSlots = countFormationRoles(formation, Set.of("LW", "RW"));
        int centralSlots = Math.max(0, slotsNeeded - wideSlots);
        int before = lineup.size();

        List<SessionPlayer> widePlayers = availablePlayers.stream()
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .filter(p -> isWideAttackingNatural(p.getPosition()))
            .limit(wideSlots)
            .collect(Collectors.toList());
        lineup.addAll(widePlayers);
        widePlayers.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));

        List<SessionPlayer> centralPlayers = availablePlayers.stream()
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .filter(p -> lineupHelper.isAttacker(p.getPosition()))
            .limit(centralSlots)
            .collect(Collectors.toList());
        lineup.addAll(centralPlayers);
        centralPlayers.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));

        int stillNeeded = slotsNeeded - (lineup.size() - before);
        if (stillNeeded > 0) {
            List<SessionPlayer> fallback = availablePlayers.stream()
                .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
                .limit(stillNeeded)
                .collect(Collectors.toList());
            lineup.addAll(fallback);
            fallback.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));
            long offPosCount = fallback.stream()
                .filter(p -> !isAutoSelectAttackingCandidate(p.getPosition(), true))
                .count();
            if (offPosCount > 0) {
                warnings.add(LineupWarningDTO.offPositionFill("ATT", (int) offPosCount));
            }
        }
    }

    private int countFormationRoles(Formation formation, Set<String> roles) {
        if (formationService == null || formation == null || roles == null || roles.isEmpty()) {
            return 0;
        }
        FormationDTO formationDto = formationService.getFormationByName(formation.getCode());
        if (formationDto == null || formationDto.positions() == null) {
            return 0;
        }
        return (int) formationDto.positions().stream()
            .filter(pos -> roles.contains(pos.role()))
            .count();
    }

    private boolean isWideAttackingNatural(String playerPosition) {
        if (playerPosition == null) {
            return false;
        }
        return switch (playerPosition.toUpperCase()) {
            case "WINGER", "LW", "RW", "LM", "RM" -> true;
            default -> false;
        };
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
                                      Map<String, LineupSlotDTO> slotMap) {
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
        // aggregate. V25D99.20.2-BACK: slotMap is now Map<String, LineupSlotDTO>
        // (subdivisionId → LineupSlotDTO with playerId + customX/Y). Pass the
        // LineupSlotDTO values through directly so the front's free-positioning
        // customXPercent / customYPercent (V25D98 model) survive the round-trip
        // into the LineupDTO and downstream FormationEffectiveness.from() can
        // apply the distance-from-ideal penalty at the actual drop point.
        //
        // V25D52 (Sprint C13b): LineupSlotDTO is record(playerId, subdivisionId)
        // — args MUST be (playerId, subdivisionId). slotMap is keyed by
        // subdivisionId with LineupSlotDTO values (whose playerId is the
        // inner field). Pre-V25D99.20.2, the map was keyed by subdivisionId
        // with String playerId values, and the constructor call was
        // (e.getValue(), e.getKey()) — now it's (slot.playerId(),
        // slot.subdivisionId()) since the outer key + inner field agree.
        List<LineupSlotDTO> slots = (slotMap == null || slotMap.isEmpty())
                ? List.of()
                : slotMap.entrySet().stream()
                    .map(e -> {
                        LineupSlotDTO inner = e.getValue();
                        // Prefer the LineupSlotDTO's own subdivisionId (it
                        // may differ from the outer key for legacy
                        // pre-V25D99.20.2 wrapped values, or for null
                        // subdivisionId in the inner DTO). Fall back to
                        // the outer key when the inner is null.
                        String subdivisionId = inner.subdivisionId() != null
                                ? inner.subdivisionId()
                                : e.getKey();
                        return new LineupSlotDTO(
                                inner.playerId(),
                                subdivisionId,
                                inner.customXPercent(),
                                inner.customYPercent());
                    })
                    .toList();
        Map<String, String> naturalByPlayer = new HashMap<>();
        for (SessionPlayer p : players) {
            if (p.getSessionPlayerId() != null && p.getPosition() != null) {
                naturalByPlayer.put(p.getSessionPlayerId(), p.getPosition());
            }
        }
        // V25D99.15-BACK: build per-player attribute DTOs so the
        // FormationEffectiveness pipeline can compute the engine's
        // teamAttack / teamDefense / teamMidfield aggregates. Without
        // attributes the calculator falls back to median 70 per stat.
        List<FormationEffectiveness.PlayerAttrDTO> attrsByPlayer = new ArrayList<>();
        for (SessionPlayer p : players) {
            if (p.getSessionPlayerId() != null) {
                attrsByPlayer.add(new FormationEffectiveness.PlayerAttrDTO(
                        p.getSessionPlayerId(),
                        p.getAttack(),
                        p.getDefense(),
                        p.getTechnique(),
                        p.getMentality()));
            }
        }
        // V25D55 (Sprint C16): manual-select just persisted formation.getCode()
        // into CareerSave.teamStarting11Formation (line above). Pass it through
        // so the inferredFormation field matches the actual selected label
        // (e.g., "3-5-2-CDM") instead of collapsing to a 3-DIGIT triple.
        //
        // V25D99.16-BACK: resolve per-subdivision xPct/yPct from the
        // FormationService cache so the team ratings use the new
        // distance-aware effectiveness instead of the legacy zone-only
        // table. Without this, fine-grained drag-and-drop on the field
        // produces no rating change.
        Map<String, double[]> coordsBySubdivision =
                formationService.getCoordsByFormation(formation.getCode());
        FormationEffectiveness formationEffectiveness =
                FormationEffectiveness.from(
                        slots,
                        naturalByPlayer,
                        formation.getCode(),
                        attrsByPlayer,
                        formation.getCode(),
                        coordsBySubdivision);

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
     * @return subdivisionId → LineupSlotDTO map. For auto-select the
     *         customX/Y are null (canonical snap-to-slot). For manual-select
     *         the front's overrides are applied on top in
     *         {@link #manualSelectLineupWithSlots}. May have fewer entries
     *         than formation positions when {@code isAutoSelect} is
     *         {@code false}.
     */
    private Map<String, LineupSlotDTO> buildAutoSelectSlotMap(
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
        Map<String, LineupSlotDTO> slotMap = new HashMap<>();
        Set<String> usedPlayerIds = new HashSet<>();
        List<FormationPositionDTO> positions = formationDto.positions();
        for (int positionIndex = 0; positionIndex < positions.size(); positionIndex++) {
            FormationPositionDTO pos = positions.get(positionIndex);
            String role = pos.role();
            String subdivisionId = pos.subdivisionId();
            if (role == null || subdivisionId == null || subdivisionId.isBlank()) {
                continue;
            }
            boolean assigned = false;
            // Phase 1: helper/category-compatible match.
            SessionPlayer bestMatch = null;
            int bestScore = Integer.MIN_VALUE;
            for (SessionPlayer player : lineup) {
                String playerId = player.getSessionPlayerId();
                if (playerId == null || usedPlayerIds.contains(playerId)) {
                    continue;
                }
                boolean compatible = isAutoSelect
                    ? autoSelectSlotMatch(role, player.getPosition())
                    : categorySlotMatch(role, player.getPosition());
                if (!compatible) {
                    continue;
                }
                if (isAutoSelect
                    && shouldReserveCentralForwardForRemainingSlots(
                        role,
                        player.getPosition(),
                        lineup,
                        usedPlayerIds,
                        positions,
                        positionIndex)) {
                    continue;
                }
                int score = isAutoSelect ? roleFitScore(role, player.getPosition()) : 1;
                if (bestMatch == null || score > bestScore) {
                    bestMatch = player;
                    bestScore = score;
                }
                if (!isAutoSelect) {
                    break;
                    // V25D99.20.2-BACK: store LineupSlotDTO with playerId +
                    // subdivisionId. customX/Y null at auto-select stage
                    // (canonical coords only — manual-select overrides
                    // these if the front sent free-positioning coords).
                }
            }
            if (bestMatch != null) {
                String playerId = bestMatch.getSessionPlayerId();
                slotMap.put(subdivisionId, new LineupSlotDTO(playerId, subdivisionId, null, null));
                usedPlayerIds.add(playerId);
                assigned = true;
            }
            // Phase 2: category-compatible match. This preserves robustness for
            // broad seed roles (DEF/MID/ATT) and thin squads, but only after
            // trying a specific slot-role fit first.
            if (!assigned) {
                for (SessionPlayer player : lineup) {
                    String playerId = player.getSessionPlayerId();
                    if (playerId == null || usedPlayerIds.contains(playerId)) {
                        continue;
                    }
                    if (categorySlotMatch(role, player.getPosition())) {
                        slotMap.put(subdivisionId, new LineupSlotDTO(playerId, subdivisionId, null, null));
                        usedPlayerIds.add(playerId);
                        assigned = true;
                        break;
                    }
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
                    // V25D99.20.2-BACK: wrap the off-position fallback playerId
                    // in a LineupSlotDTO with the subdivisionId and no
                    // customX/Y override (canonical coords for off-position
                    // players, penalty surfaced by FormationEffectiveness
                    // downstream).
                    slotMap.put(subdivisionId, new LineupSlotDTO(playerId, subdivisionId, null, null));
                    usedPlayerIds.add(playerId);
                    break;
                }
            }
        }
        return slotMap;
    }

    private boolean categorySlotMatch(String role, String playerPosition) {
        return switch (role) {
            case "GK" -> "GK".equals(playerPosition);
            case "LB", "CB", "RB", "LWB", "RWB" -> lineupHelper.isDefender(playerPosition);
            case "CDM", "CM", "CAM", "LM", "RM" -> lineupHelper.isMidfielder(playerPosition);
            case "LW", "RW" -> roleAwareSlotMatch(role, playerPosition) || lineupHelper.isAttacker(playerPosition);
            case "CF", "ST" -> lineupHelper.isAttacker(playerPosition);
            default -> false;
        };
    }

    private boolean autoSelectSlotMatch(String role, String playerPosition) {
        return roleAwareSlotMatch(role, playerPosition) || categorySlotMatch(role, playerPosition);
    }

    private boolean shouldReserveCentralForwardForRemainingSlots(
            String currentRole,
            String playerPosition,
            List<SessionPlayer> lineup,
            Set<String> usedPlayerIds,
            List<FormationPositionDTO> positions,
            int currentPositionIndex) {
        if (isCentralForwardRole(currentRole) || !isCentralForwardPosition(playerPosition)) {
            return false;
        }
        int remainingCentralForwardSlots = 0;
        for (int i = currentPositionIndex + 1; i < positions.size(); i++) {
            if (isCentralForwardRole(positions.get(i).role())) {
                remainingCentralForwardSlots++;
            }
        }
        if (remainingCentralForwardSlots <= 0) {
            return false;
        }
        long unusedCentralForwards = lineup.stream()
            .filter(player -> player.getSessionPlayerId() != null)
            .filter(player -> !usedPlayerIds.contains(player.getSessionPlayerId()))
            .filter(player -> isCentralForwardPosition(player.getPosition()))
            .count();
        return unusedCentralForwards <= remainingCentralForwardSlots;
    }

    private boolean isCentralForwardRole(String role) {
        return "ST".equals(role) || "CF".equals(role);
    }

    private boolean isCentralForwardPosition(String position) {
        return "ST".equals(position) || "CF".equals(position) || "ATT".equals(position);
    }

    private boolean roleAwareSlotMatch(String role, String playerPosition) {
        if (role == null || playerPosition == null) {
            return false;
        }
        String r = role.toUpperCase();
        String p = playerPosition.toUpperCase();
        if (r.equals(p)) {
            return true;
        }
        return switch (r) {
            case "GK" -> "GK".equals(p);
            case "CB" -> p.equals("DEF") || p.equals("CB");
            case "LB" -> p.equals("DEF") || p.equals("LB") || p.equals("LWB");
            case "RB" -> p.equals("DEF") || p.equals("RB") || p.equals("RWB");
            case "LWB" -> p.equals("LWB") || p.equals("LB") || p.equals("LM") || p.equals("LW") || p.equals("WINGER");
            case "RWB" -> p.equals("RWB") || p.equals("RB") || p.equals("RM") || p.equals("RW") || p.equals("WINGER");
            case "CDM" -> p.equals("MID") || p.equals("CDM") || p.equals("DM") || p.equals("CM");
            case "CM" -> p.equals("MID") || p.equals("CM") || p.equals("CDM") || p.equals("CAM") || p.equals("DM");
            case "CAM" -> p.equals("MID") || p.equals("CAM") || p.equals("AM") || p.equals("CM") || p.equals("CF");
            case "LM" -> p.equals("MID") || p.equals("LM") || p.equals("LW") || p.equals("LWB") || p.equals("WINGER");
            case "RM" -> p.equals("MID") || p.equals("RM") || p.equals("RW") || p.equals("RWB") || p.equals("WINGER");
            case "LW" -> p.equals("LW") || p.equals("LM") || p.equals("WINGER");
            case "RW" -> p.equals("RW") || p.equals("RM") || p.equals("WINGER");
            case "CF" -> p.equals("ATT") || p.equals("CF") || p.equals("ST") || p.equals("CAM") || p.equals("AM");
            case "ST" -> p.equals("ATT") || p.equals("ST") || p.equals("CF");
            default -> false;
        };
    }

    private int roleFitScore(String role, String playerPosition) {
        if (role == null || playerPosition == null) {
            return -100;
        }
        String r = role.toUpperCase();
        String p = playerPosition.toUpperCase();
        if (r.equals(p)) {
            return 100;
        }
        int specificScore = specificWideRoleFitScore(r, p);
        if (specificScore > Integer.MIN_VALUE) {
            return specificScore;
        }
        if (roleAwareSlotMatch(role, playerPosition)) {
            return 80;
        }
        if (categorySlotMatch(role, playerPosition)) {
            return 10;
        }
        return -100;
    }

    private int specificWideRoleFitScore(String role, String playerPosition) {
        return switch (role) {
            case "LM", "RM" -> switch (playerPosition) {
                case "WINGER" -> 96;
                case "LW", "RW" -> 94;
                case "LWB", "RWB" -> 90;
                case "MID" -> 65;
                default -> Integer.MIN_VALUE;
            };
            case "LW", "RW" -> switch (playerPosition) {
                case "WINGER" -> 96;
                case "LM", "RM" -> 92;
                case "ATT" -> 70;
                default -> Integer.MIN_VALUE;
            };
            case "LWB", "RWB" -> switch (playerPosition) {
                case "WINGER" -> 94;
                case "LM", "RM" -> 92;
                case "LB", "RB" -> 90;
                case "DEF" -> 65;
                default -> Integer.MIN_VALUE;
            };
            default -> Integer.MIN_VALUE;
        };
    }
}
