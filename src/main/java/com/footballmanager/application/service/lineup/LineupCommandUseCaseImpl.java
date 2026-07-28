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
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.port.in.lineup.LineupCommandUseCase;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.TeamChemistryCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LineupCommandUseCaseImpl implements LineupCommandUseCase {

    private final CareerSessionService careerSessionService;
    private final LineupHelper lineupHelper;
    private final FormationService formationService;

    @Override
    public Mono<LineupDTO> autoSelectLineup(UUID userId, String formationCode) {
        Formation formation = Formation.fromString(formationCode);
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
                Map<String, LineupSlotDTO> slotMap = buildAutoSelectSlotMap(formation, lineup, true);
                if (slotMap.size() != LineupRules.TARGET_LINEUP_PLAYERS) {
                    throw new IllegalStateException(
                        "Auto-select slot assignment incomplete: " + slotMap.size()
                        + " / " + LineupRules.TARGET_LINEUP_PLAYERS
                        + " (formation: " + formation.getCode() + ", squad may be too small)"
                    );
                }
                career.replaceTeamStarting11SubdivisionRaw(userTeamId, slotMap);
                career.getTeamStarting11Formation().put(userTeamId, formation.getCode());
                syncSessionTeamFormation(career, userTeamId, formation.getCode());

                return careerSessionService.saveCareer(career)
                    .thenReturn(buildLineupDTO(lineup, formation, warnings, slotMap));
            });
    }

    @Override
    public Mono<LineupDTO> manualSelectLineup(UUID userId, String formationCode, List<String> playerIds) {
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
                lineupHelper.validatePlayerFitness(selectedPlayers);
                List<LineupWarningDTO> warnings = lineupHelper.detectShortHandedWarnings(selectedPlayers);
                if (selectedPlayers.size() < LineupRules.TARGET_LINEUP_PLAYERS) {
                    warnings = new ArrayList<>(warnings);
                    warnings.add(LineupWarningDTO.shortHanded(selectedPlayers.size()));
                }

                career.getTeamStarting11().put(userTeamId, playerIds);
                career.getTeamStarting11Formation().put(userTeamId, formation.getCode());
                syncSessionTeamFormation(career, userTeamId, formation.getCode());
                boolean hasExplicitSlotOverrides = slots != null && !slots.isEmpty();
                boolean hasCustomSlotOverrides = hasExplicitSlotOverrides
                    && slots.stream().anyMatch(this::hasCustomCoordinates);
                Map<String, LineupSlotDTO> slotMap = buildAutoSelectSlotMap(
                    formation,
                    selectedPlayers,
                    !hasExplicitSlotOverrides && selectedPlayers.size() == LineupRules.TARGET_LINEUP_PLAYERS);
                if (hasExplicitSlotOverrides) {
                    for (LineupSlotDTO slot : slots) {
                        if (slot.subdivisionId() == null || slot.subdivisionId().isBlank()) {
                            continue;
                        }
                        if (slot.playerId() == null || slot.playerId().isBlank()) {
                            continue;
                        }
                        if (!playerIds.contains(slot.playerId())) {
                            continue;
                        }
                        slotMap.put(slot.subdivisionId(), slot);
                    }
                }
                career.replaceTeamStarting11SubdivisionRaw(userTeamId, slotMap);

                return careerSessionService.saveCareer(career)
                    .thenReturn(buildLineupDTO(selectedPlayers, formation, warnings, slotMap));
            });
    }

    private boolean hasCustomCoordinates(LineupSlotDTO slot) {
        return slot != null
            && ((slot.customXPercent() != null && Double.isFinite(slot.customXPercent()))
                || (slot.customYPercent() != null && Double.isFinite(slot.customYPercent())));
    }

    private void syncSessionTeamFormation(CareerSave career, String teamId, String formationCode) {
        SessionTeam team = career.getSessionTeam(teamId);
        if (team != null) {
            team.setFormation(formationCode);
        }
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
        if (availablePlayers.size() < LineupRules.TARGET_LINEUP_PLAYERS) {
            throw new NotEnoughPlayersException(
                "Auto-select requires " + LineupRules.TARGET_LINEUP_PLAYERS
                + " available players, got " + availablePlayers.size());
        }

        List<SessionPlayer> lineup = new ArrayList<>();
        List<LineupWarningDTO> warnings = new ArrayList<>();
        Set<String> alreadyTaken = new HashSet<>();
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
        OutfieldRoleNeeds roleNeeds = getOutfieldRoleNeeds(formation);
        int wideAttackingSlots = countFormationRoles(formation, Set.of("LW", "RW"));
        boolean hasWideAttackingSlots = wideAttackingSlots > 0 && roleNeeds.attackers() >= wideAttackingSlots + 1;
        int wideMidfieldSlots = countFormationRoles(formation, Set.of("LM", "RM", "LWB", "RWB"))
            + (hasWideAttackingSlots ? 0 : wideAttackingSlots);
        boolean hasWideMidfieldSlots = formationHasAnyRole(formation, Set.of("LM", "RM", "LWB", "RWB"))
            || (wideAttackingSlots > 0 && !hasWideAttackingSlots);

        fillRow(availablePlayers, lineup, alreadyTaken, warnings,
            roleNeeds.defenders(), "DEF", lineupHelper::isDefender);
        if (wideMidfieldSlots > 0) {
            fillMidfieldRowWithWideSlotPreference(availablePlayers, lineup, alreadyTaken, warnings,
                roleNeeds.midfielders(), wideMidfieldSlots, hasWideAttackingSlots);
        } else {
            fillRow(availablePlayers, lineup, alreadyTaken, warnings,
                roleNeeds.midfielders(), "MID",
                playerPosition -> isAutoSelectMidfieldCandidate(playerPosition, hasWideMidfieldSlots && !hasWideAttackingSlots));
        }
        if (hasWideAttackingSlots) {
            fillAttackingRowWithWideSlotPreference(formation, availablePlayers, lineup, alreadyTaken, warnings,
                roleNeeds.attackers(), wideAttackingSlots);
        } else {
            fillRow(availablePlayers, lineup, alreadyTaken, warnings,
                roleNeeds.attackers(), "ATT",
                playerPosition -> isAutoSelectAttackingCandidate(playerPosition, false));
        }

        includeSpecificRoleIfNeeded(formation, availablePlayers, lineup, alreadyTaken, "CAM");
        ensureWideRoleDepthIfNeeded(formation, availablePlayers, lineup, alreadyTaken);
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

    private void ensureWideRoleDepthIfNeeded(
            Formation formation,
            List<SessionPlayer> availablePlayers,
            List<SessionPlayer> lineup,
            Set<String> alreadyTaken) {
        int neededWideRoles = countFormationRoles(formation, Set.of("LW", "RW", "LM", "RM", "LWB", "RWB"));
        if (neededWideRoles <= 0) {
            return;
        }
        long selectedWideProfiles = lineup.stream()
            .filter(player -> isWideAttackingNatural(player.getPosition()))
            .count();
        int missingWideProfiles = neededWideRoles - (int) selectedWideProfiles;
        if (missingWideProfiles <= 0) {
            return;
        }

        List<SessionPlayer> availableWideProfiles = availablePlayers.stream()
            .filter(player -> player.getSessionPlayerId() != null)
            .filter(player -> !alreadyTaken.contains(player.getSessionPlayerId()))
            .filter(player -> isWideAttackingNatural(player.getPosition()))
            .limit(missingWideProfiles)
            .collect(Collectors.toList());
        for (SessionPlayer wideProfile : availableWideProfiles) {
            int replaceIndex = findReplaceableNonWideIndex(lineup);
            if (replaceIndex < 0) {
                return;
            }
            SessionPlayer replaced = lineup.get(replaceIndex);
            if (replaced.getSessionPlayerId() != null) {
                alreadyTaken.remove(replaced.getSessionPlayerId());
            }
            lineup.set(replaceIndex, wideProfile);
            alreadyTaken.add(wideProfile.getSessionPlayerId());
        }
    }

    private int findReplaceableNonWideIndex(List<SessionPlayer> lineup) {
        for (int i = lineup.size() - 1; i >= 0; i--) {
            SessionPlayer player = lineup.get(i);
            if (player == null || player.getSessionPlayerId() == null) {
                continue;
            }
            String position = player.getPosition();
            if ("GK".equals(position)
                || lineupHelper.isDefender(position)
                || isCentralForwardPosition(position)
                || isWideAttackingNatural(position)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private OutfieldRoleNeeds getOutfieldRoleNeeds(Formation formation) {
        return new OutfieldRoleNeeds(
                formation.getDefenders(),
                formation.getMidfielders(),
                formation.getAttackers());
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
            int slotsNeeded,
            int wideSlots) {
        if (slotsNeeded <= 0) {
            return;
        }

        wideSlots = Math.min(wideSlots, slotsNeeded);
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
            .filter(p -> isCentralForwardPosition(p.getPosition()))
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

    private void fillMidfieldRowWithWideSlotPreference(
            List<SessionPlayer> availablePlayers,
            List<SessionPlayer> lineup,
            Set<String> alreadyTaken,
            List<LineupWarningDTO> warnings,
            int slotsNeeded,
            int wideSlots,
            boolean reserveGenericWingersForAttack) {
        if (slotsNeeded <= 0) {
            return;
        }

        wideSlots = Math.min(wideSlots, slotsNeeded);
        int centralSlots = Math.max(0, slotsNeeded - wideSlots);
        int before = lineup.size();

        List<SessionPlayer> widePlayers = availablePlayers.stream()
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .filter(p -> reserveGenericWingersForAttack
                ? isDedicatedWideMidfieldNatural(p.getPosition())
                : isWideMidfieldNatural(p.getPosition()))
            .limit(wideSlots)
            .collect(Collectors.toList());
        lineup.addAll(widePlayers);
        widePlayers.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));

        List<SessionPlayer> centralPlayers = availablePlayers.stream()
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .filter(p -> lineupHelper.isMidfielder(p.getPosition()))
            .limit(centralSlots)
            .collect(Collectors.toList());
        lineup.addAll(centralPlayers);
        centralPlayers.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));

        int stillNeeded = slotsNeeded - (lineup.size() - before);
        if (stillNeeded > 0) {
            List<SessionPlayer> fallbackPool = availablePlayers.stream()
                .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
                .collect(Collectors.toList());
            List<SessionPlayer> nonCentralForwardFallbackPool = fallbackPool.stream()
                .filter(p -> !isCentralForwardPosition(p.getPosition()))
                .collect(Collectors.toList());
            if (nonCentralForwardFallbackPool.size() >= stillNeeded) {
                fallbackPool = nonCentralForwardFallbackPool;
            }
            List<SessionPlayer> fallback = fallbackPool.stream()
                .sorted(
                    Comparator
                        .comparingInt((SessionPlayer p) -> tacticalFallbackScore("MID", p.getPosition()))
                        .reversed()
                        .thenComparing(Comparator.comparing(SessionPlayer::calculateOverall).reversed()))
                .limit(stillNeeded)
                .collect(Collectors.toList());
            lineup.addAll(fallback);
            fallback.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));
            long offPosCount = fallback.stream()
                .filter(p -> !isAutoSelectMidfieldCandidate(p.getPosition(), true))
                .count();
            if (offPosCount > 0) {
                warnings.add(LineupWarningDTO.offPositionFill("MID", (int) offPosCount));
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

    private boolean isWideMidfieldNatural(String playerPosition) {
        if (playerPosition == null) {
            return false;
        }
        return switch (playerPosition.toUpperCase(Locale.ROOT)) {
            case "WINGER", "LW", "RW", "LM", "RM", "LWB", "RWB", "LB", "RB" -> true;
            default -> false;
        };
    }

    private boolean isDedicatedWideMidfieldNatural(String playerPosition) {
        if (playerPosition == null) {
            return false;
        }
        return switch (playerPosition.toUpperCase(Locale.ROOT)) {
            case "LM", "RM", "LWB", "RWB", "LB", "RB" -> true;
            default -> false;
        };
    }

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
        List<SessionPlayer> perfect = availablePlayers.stream()
            .filter(p -> positionMatcher.test(p.getPosition()))
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .limit(slotsNeeded)
            .collect(Collectors.toList());
        lineup.addAll(perfect);
        perfect.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));
        int stillNeeded = slotsNeeded - perfect.size();
        if (stillNeeded > 0) {
            List<SessionPlayer> offPosFill = availablePlayers.stream()
                .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
                .sorted(
                    Comparator
                        .comparingInt((SessionPlayer p) -> tacticalFallbackScore(positionGroup, p.getPosition()))
                        .reversed()
                        .thenComparing(Comparator.comparing(SessionPlayer::calculateOverall).reversed()))
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

    private int tacticalFallbackScore(String positionGroup, String playerPosition) {
        if (positionGroup == null || playerPosition == null) {
            return 0;
        }
        String group = positionGroup.toUpperCase();
        String pos = playerPosition.toUpperCase();
        return switch (group) {
            case "DEF" -> switch (pos) {
                case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> 100;
                case "CDM", "DM", "CM", "MID" -> 65;
                case "LM", "RM", "LW", "RW", "WINGER" -> 45;
                default -> 10;
            };
            case "MID" -> switch (pos) {
                case "MID", "CM", "CDM", "DM", "CAM", "AM", "LM", "RM", "LW", "RW" -> 100;
                case "WINGER", "LWB", "RWB" -> 75;
                case "DEF", "CB", "LB", "RB" -> 55;
                case "CF", "ST", "ATT" -> 35;
                default -> 10;
            };
            case "ATT" -> switch (pos) {
                case "ATT", "CF", "ST", "LW", "RW", "WINGER" -> 100;
                case "CAM", "AM", "LM", "RM", "MID" -> 65;
                case "CM", "CDM", "DM" -> 45;
                default -> 10;
            };
            default -> 0;
        };
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
        List<LineupSlotDTO> slots = (slotMap == null || slotMap.isEmpty())
                ? List.of()
                : slotMap.entrySet().stream()
                    .map(e -> {
                        LineupSlotDTO inner = e.getValue();
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
        List<FormationPositionDTO> assignmentPositions = isAutoSelect
            ? positions.stream()
                .sorted(Comparator.comparingInt(this::autoSelectAssignmentPriority))
                .toList()
            : positions;
        for (int positionIndex = 0; positionIndex < assignmentPositions.size(); positionIndex++) {
            FormationPositionDTO pos = assignmentPositions.get(positionIndex);
            String role = pos.role();
            String subdivisionId = pos.subdivisionId();
            if (role == null || subdivisionId == null || subdivisionId.isBlank()) {
                continue;
            }
            boolean assigned = false;
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
                        assignmentPositions,
                        positionIndex)) {
                    continue;
                }
                if (isAutoSelect
                    && shouldReserveWideFallbackForRemainingMidfieldSlots(
                        role,
                        player.getPosition(),
                        lineup,
                        usedPlayerIds,
                        assignmentPositions,
                        positionIndex)) {
                    continue;
                }
                int score = isAutoSelect
                    ? roleFitScore(role, player.getPosition()) + curatedRoleSlotBonus(player, pos)
                    : 1;
                if (bestMatch == null
                    || score > bestScore
                    || (score == bestScore && player.calculateOverall() > bestMatch.calculateOverall())) {
                    bestMatch = player;
                    bestScore = score;
                }
                if (!isAutoSelect) {
                    break;
                }
            }
            if (bestMatch != null) {
                String playerId = bestMatch.getSessionPlayerId();
                slotMap.put(subdivisionId, new LineupSlotDTO(playerId, subdivisionId, null, null));
                usedPlayerIds.add(playerId);
                assigned = true;
            }
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
            if (!assigned && isAutoSelect) {
                lineup.stream()
                    .filter(player -> player.getSessionPlayerId() != null)
                    .filter(player -> !usedPlayerIds.contains(player.getSessionPlayerId()))
                    .max(
                        Comparator
                            .comparingInt((SessionPlayer player) -> roleFitScore(role, player.getPosition()))
                            .thenComparingInt(player -> tacticalFallbackScore(tacticalPositionGroupForRole(role), player.getPosition()))
                            .thenComparing(SessionPlayer::calculateOverall))
                    .ifPresent(player -> {
                        String playerId = player.getSessionPlayerId();
                        slotMap.put(subdivisionId, new LineupSlotDTO(playerId, subdivisionId, null, null));
                        usedPlayerIds.add(playerId);
                    });
            }
        }
        return slotMap;
    }

    private String tacticalPositionGroupForRole(String role) {
        if (role == null) {
            return "";
        }
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "LB", "CB", "RB" -> "DEF";
            case "LWB", "RWB", "CDM", "CM", "CAM", "LM", "RM" -> "MID";
            case "LW", "RW", "CF", "ST" -> "ATT";
            default -> "";
        };
    }

    private int autoSelectAssignmentPriority(FormationPositionDTO position) {
        if (position == null || position.role() == null) {
            return 99;
        }
        return switch (position.role().toUpperCase(Locale.ROOT)) {
            case "GK" -> 0;
            case "ST", "CF" -> 10;
            case "CB" -> 20;
            case "LW", "RW" -> 25;
            case "CDM", "CM", "CAM" -> 30;
            case "LB", "RB" -> 40;
            case "LWB", "RWB" -> 50;
            case "LM", "RM" -> 60;
            default -> 90;
        };
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

    private boolean shouldReserveWideFallbackForRemainingMidfieldSlots(
            String currentRole,
            String playerPosition,
            List<SessionPlayer> lineup,
            Set<String> usedPlayerIds,
            List<FormationPositionDTO> positions,
            int currentPositionIndex) {
        if (isCentralMidfieldRole(currentRole) || "LM".equals(currentRole) || "RM".equals(currentRole) || !isWideMidfieldFallbackPosition(playerPosition)) {
            return false;
        }
        int remainingMidfieldSlots = 0;
        for (int i = currentPositionIndex + 1; i < positions.size(); i++) {
            String remainingRole = positions.get(i).role();
            if (isCentralMidfieldRole(remainingRole) || "LM".equals(remainingRole) || "RM".equals(remainingRole)) {
                remainingMidfieldSlots++;
            }
        }
        if (remainingMidfieldSlots <= 0) {
            return false;
        }
        long unusedNaturalMidfieldFits = lineup.stream()
            .filter(player -> player.getSessionPlayerId() != null)
            .filter(player -> !usedPlayerIds.contains(player.getSessionPlayerId()))
            .filter(player -> !isSamePositionFamily(player.getPosition(), playerPosition))
            .filter(player -> isNaturalMidfieldPosition(player.getPosition()))
            .count();
        return unusedNaturalMidfieldFits < remainingMidfieldSlots;
    }

    private boolean isCentralForwardRole(String role) {
        return "ST".equals(role) || "CF".equals(role);
    }

    private boolean isCentralForwardPosition(String position) {
        return "ST".equals(position) || "CF".equals(position) || "ATT".equals(position);
    }

    private boolean isCentralMidfieldRole(String role) {
        return "CDM".equals(role) || "CM".equals(role) || "CAM".equals(role);
    }

    private boolean isNaturalMidfieldPosition(String position) {
        return "MID".equals(position)
            || "CM".equals(position)
            || "CDM".equals(position)
            || "DM".equals(position)
            || "CAM".equals(position)
            || "AM".equals(position)
            || "LM".equals(position)
            || "RM".equals(position)
            || "LW".equals(position)
            || "RW".equals(position);
    }

    private boolean isWideMidfieldFallbackPosition(String position) {
        return "WINGER".equals(position)
            || "LW".equals(position)
            || "RW".equals(position)
            || "LM".equals(position)
            || "RM".equals(position)
            || "LWB".equals(position)
            || "RWB".equals(position);
    }

    private boolean isSamePositionFamily(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
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
            case "CM" -> p.equals("MID") || p.equals("CM") || p.equals("CDM") || p.equals("CAM") || p.equals("DM") || p.equals("WINGER");
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
        int midfieldFallbackScore = specificCentralMidfieldFallbackScore(r, p);
        if (midfieldFallbackScore > Integer.MIN_VALUE) {
            return midfieldFallbackScore;
        }
        if (roleAwareSlotMatch(role, playerPosition)) {
            return 80;
        }
        if (categorySlotMatch(role, playerPosition)) {
            return 10;
        }
        return -100;
    }

    private int curatedRoleSlotBonus(SessionPlayer player, FormationPositionDTO slot) {
        CuratedPlayerRoleProfile profile = curatedPlayerRoleProfile(player);
        if (profile == null || slot == null || slot.role() == null) {
            return 0;
        }
        String role = slot.role().toUpperCase(Locale.ROOT);
        int bonus = 0;
        if (profile.roles().contains(role)) {
            bonus += 26;
        } else if (curatedRoleFamilyMatch(profile.roles(), role)) {
            bonus += 12;
        }
        String slotSide = tacticalSlotSide(slot);
        if ("LEFT".equals(slotSide) || "RIGHT".equals(slotSide)) {
            if (profile.sides().contains(slotSide) || profile.sides().contains("BOTH")) {
                bonus += 22;
            } else if (profile.sides().contains(oppositeSide(slotSide))) {
                bonus -= 34;
            }
        } else if ("CENTER".equals(slotSide) && profile.sides().contains("CENTER")) {
            bonus += 8;
        }
        return bonus;
    }

    private boolean curatedRoleFamilyMatch(Set<String> playerRoles, String slotRole) {
        if (Set.of("LB", "LWB", "LM", "LW").contains(slotRole)) {
            return playerRoles.stream().anyMatch(Set.of("LB", "LWB", "LM", "LW")::contains);
        }
        if (Set.of("RB", "RWB", "RM", "RW").contains(slotRole)) {
            return playerRoles.stream().anyMatch(Set.of("RB", "RWB", "RM", "RW")::contains);
        }
        if (Set.of("CB", "CDM", "CM", "CAM", "ST", "CF").contains(slotRole)) {
            return playerRoles.stream().anyMatch(Set.of("CB", "CDM", "CM", "CAM", "ST", "CF")::contains);
        }
        return false;
    }

    private String tacticalSlotSide(FormationPositionDTO slot) {
        String role = slot.role() != null ? slot.role().toUpperCase(Locale.ROOT) : "";
        if (Set.of("LB", "LWB", "LM", "LW").contains(role)) return "LEFT";
        if (Set.of("RB", "RWB", "RM", "RW").contains(role)) return "RIGHT";
        if (Set.of("GK", "CB", "CDM", "CM", "ST", "CF").contains(role)) return "CENTER";
        Double x = slot.xPercent();
        if (x != null && x <= 42) return "LEFT";
        if (x != null && x >= 58) return "RIGHT";
        return "CENTER";
    }

    private String oppositeSide(String side) {
        return "LEFT".equals(side) ? "RIGHT" : "LEFT";
    }

    private CuratedPlayerRoleProfile curatedPlayerRoleProfile(SessionPlayer player) {
        if (player == null || player.getName() == null) {
            return null;
        }
        return switch (normalizePlayerName(player.getName())) {
            case "dani carvajal" -> new CuratedPlayerRoleProfile(Set.of("RB", "RWB"), Set.of("RIGHT"));
            case "david alaba" -> new CuratedPlayerRoleProfile(Set.of("CB", "LB"), Set.of("LEFT", "CENTER"));
            case "ferland mendy", "fran garcia" -> new CuratedPlayerRoleProfile(Set.of("LB", "LWB"), Set.of("LEFT"));
            case "lucas vazquez" -> new CuratedPlayerRoleProfile(Set.of("RB", "RM", "RWB"), Set.of("RIGHT"));
            case "vinicius junior" -> new CuratedPlayerRoleProfile(Set.of("LW", "LM"), Set.of("LEFT"));
            case "rodrygo goes" -> new CuratedPlayerRoleProfile(Set.of("RW", "LW", "ST", "CF"), Set.of("RIGHT", "BOTH"));
            case "brahim diaz" -> new CuratedPlayerRoleProfile(Set.of("RW", "CAM", "RM"), Set.of("RIGHT", "CENTER"));
            case "federico valverde" -> new CuratedPlayerRoleProfile(Set.of("CM", "RM", "CDM"), Set.of("CENTER", "RIGHT"));
            case "eduardo camavinga" -> new CuratedPlayerRoleProfile(Set.of("CM", "CDM", "LB"), Set.of("CENTER", "LEFT"));
            default -> null;
        };
    }

    private String normalizePlayerName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    private record CuratedPlayerRoleProfile(Set<String> roles, Set<String> sides) {}

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
            case "LWB" -> switch (playerPosition) {
                case "LW", "LM", "LWB", "LB" -> 94;
                case "WINGER" -> 90;
                case "DEF" -> 65;
                case "RW", "RM", "RWB", "RB" -> 25;
                default -> Integer.MIN_VALUE;
            };
            case "RWB" -> switch (playerPosition) {
                case "RW", "RM", "RWB", "RB" -> 94;
                case "WINGER" -> 90;
                case "DEF" -> 65;
                case "LW", "LM", "LWB", "LB" -> 25;
                default -> Integer.MIN_VALUE;
            };
            default -> Integer.MIN_VALUE;
        };
    }

    private int specificCentralMidfieldFallbackScore(String role, String playerPosition) {
        return switch (role) {
            case "CDM", "CM", "CAM" -> switch (playerPosition) {
                case "LM", "RM" -> 45;
                case "WINGER", "LWB", "RWB" -> 35;
                case "LW", "RW" -> 25;
                case "ATT", "CF", "ST" -> 5;
                default -> Integer.MIN_VALUE;
            };
            default -> Integer.MIN_VALUE;
        };
    }
}
