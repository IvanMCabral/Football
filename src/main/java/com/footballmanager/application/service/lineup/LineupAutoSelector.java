package com.footballmanager.application.service.lineup;

import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.port.in.lineup.LineupWarning;
import com.footballmanager.domain.service.LineupRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class LineupAutoSelector {

    private final LineupHelper lineupHelper;
    private final FormationService formationService;

    LineupAutoSelector(LineupHelper lineupHelper, FormationService formationService) {
        this.lineupHelper = lineupHelper;
        this.formationService = formationService;
    }

    record AutoSelectResult(List<SessionPlayer> lineup, List<LineupWarning> warnings) {}
    record OutfieldRoleNeeds(int defenders, int midfielders, int attackers) {}

    AutoSelectResult performAutoSelect(CareerSave career, String teamId, Formation formation) {
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
        List<LineupWarning> warnings = new ArrayList<>();
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
            warnings.add(LineupWarning.noGoalkeeper(availablePlayers.size()));
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
            if (LineupRoleRules.isCentralForwardPosition(selected.getPosition()) || lineupHelper.isDefender(selected.getPosition())) {
                continue;
            }
            if (LineupRoleRules.roleAwareSlotMatch(role, selected.getPosition(), lineupHelper)) {
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
        FormationDefinition formationDto = formationService.getFormationByName(formation.getCode());
        return formationDto != null
            && formationDto.positions() != null
            && formationDto.positions().stream().anyMatch(pos -> role.equals(pos.role()));
    }

    private boolean formationHasAnyRole(Formation formation, Set<String> roles) {
        if (formationService == null || formation == null || roles == null || roles.isEmpty()) {
            return false;
        }
        FormationDefinition formationDto = formationService.getFormationByName(formation.getCode());
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
        return LineupRoleRules.roleAwareSlotMatch(role, playerPosition, lineupHelper);
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
            .filter(player -> LineupRoleRules.isWideAttackingNatural(player.getPosition()))
            .count();
        int missingWideProfiles = neededWideRoles - (int) selectedWideProfiles;
        if (missingWideProfiles <= 0) {
            return;
        }

        List<SessionPlayer> availableWideProfiles = availablePlayers.stream()
            .filter(player -> player.getSessionPlayerId() != null)
            .filter(player -> !alreadyTaken.contains(player.getSessionPlayerId()))
            .filter(player -> LineupRoleRules.isWideAttackingNatural(player.getPosition()))
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
                || LineupRoleRules.isCentralForwardPosition(position)
                || LineupRoleRules.isWideAttackingNatural(position)) {
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
            List<LineupWarning> warnings,
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
            .filter(p -> LineupRoleRules.isWideAttackingNatural(p.getPosition()))
            .limit(wideSlots)
            .collect(Collectors.toList());
        lineup.addAll(widePlayers);
        widePlayers.forEach(p -> alreadyTaken.add(p.getSessionPlayerId()));

        List<SessionPlayer> centralPlayers = availablePlayers.stream()
            .filter(p -> !alreadyTaken.contains(p.getSessionPlayerId()))
            .filter(p -> LineupRoleRules.isCentralForwardPosition(p.getPosition()))
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
                warnings.add(LineupWarning.offPositionFill("ATT", (int) offPosCount));
            }
        }
    }

    private void fillMidfieldRowWithWideSlotPreference(
            List<SessionPlayer> availablePlayers,
            List<SessionPlayer> lineup,
            Set<String> alreadyTaken,
            List<LineupWarning> warnings,
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
                ? LineupRoleRules.isDedicatedWideMidfieldNatural(p.getPosition())
                : LineupRoleRules.isWideMidfieldNatural(p.getPosition()))
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
                .filter(p -> !LineupRoleRules.isCentralForwardPosition(p.getPosition()))
                .collect(Collectors.toList());
            if (nonCentralForwardFallbackPool.size() >= stillNeeded) {
                fallbackPool = nonCentralForwardFallbackPool;
            }
            List<SessionPlayer> fallback = fallbackPool.stream()
                .sorted(
                    Comparator
                        .comparingInt((SessionPlayer p) -> LineupRoleRules.tacticalFallbackScore("MID", p.getPosition()))
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
                warnings.add(LineupWarning.offPositionFill("MID", (int) offPosCount));
            }
        }
    }

    private int countFormationRoles(Formation formation, Set<String> roles) {
        if (formationService == null || formation == null || roles == null || roles.isEmpty()) {
            return 0;
        }
        FormationDefinition formationDto = formationService.getFormationByName(formation.getCode());
        if (formationDto == null || formationDto.positions() == null) {
            return 0;
        }
        return (int) formationDto.positions().stream()
            .filter(pos -> roles.contains(pos.role()))
            .count();
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

private void fillRow(List<SessionPlayer> availablePlayers,
                         List<SessionPlayer> lineup,
                         Set<String> alreadyTaken,
                         List<LineupWarning> warnings,
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
                        .comparingInt((SessionPlayer p) -> LineupRoleRules.tacticalFallbackScore(positionGroup, p.getPosition()))
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
                warnings.add(LineupWarning.offPositionFill(positionGroup, (int) offPosCount));
            }
        }
    }

    private boolean isPlayerAvailable(SessionPlayer player) {
        if (player == null || Boolean.TRUE.equals(player.getInjured())) {
            return false;
        }
        return player.getInjuryRemainingMatches() == null || player.getInjuryRemainingMatches() <= 0;
    }

    private boolean isCentralForwardPosition(String position) {
        if (position == null) {
            return false;
        }
        return switch (position.toUpperCase(Locale.ROOT)) {
            case "ATT", "ST", "CF" -> true;
            default -> false;
        };
    }


}

