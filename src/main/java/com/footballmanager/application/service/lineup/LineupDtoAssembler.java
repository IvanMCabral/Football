package com.footballmanager.application.service.lineup;
import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.service.editor.FormationPosition;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.TacticalChemistry;
import com.footballmanager.domain.model.valueobject.TacticalChemistryCalculator;
import com.footballmanager.domain.model.valueobject.TeamChemistryCalculator;
import com.footballmanager.domain.port.in.lineup.LineupPlayerView;
import com.footballmanager.domain.port.in.lineup.LineupView;
import com.footballmanager.domain.port.in.lineup.LineupWarning;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
final class LineupDtoAssembler {
    private final FormationService formationService;
    private final LineupHelper lineupHelper;
    LineupDtoAssembler(FormationService formationService, LineupHelper lineupHelper) {
        this.formationService = formationService;
        this.lineupHelper = lineupHelper;
    }
    LineupView buildLineupDTO(List<SessionPlayer> players, Formation formation,
                                      List<LineupWarning> warnings,
                                      Map<String, LineupSlot> slotMap) {
        List<LineupPlayerView> playerDTOs = players.stream()
            .map(p -> new LineupPlayerView(
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
        List<LineupSlot> slots = (slotMap == null || slotMap.isEmpty())
                ? List.of()
                : slotMap.entrySet().stream()
                    .map(e -> {
                        LineupSlot inner = e.getValue();
                        String subdivisionId = inner.subdivisionId() != null
                                ? inner.subdivisionId()
                                : e.getKey();
                        return new LineupSlot(
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
        TacticalChemistry tacticalChemistry = TacticalChemistryCalculator.calculate(
                slots,
                naturalByPlayer,
                coordsBySubdivision);
        ChemistryDetail chemistryDetail = TeamChemistryCalculator.calculate(players);
        return new LineupView(formation.getCode(), playerDTOs, false, warnings, slots,
                chemistryDetail.score(),
                chemistryDetail,
                tacticalChemistry,
                formationEffectiveness);
    }
Map<String, LineupSlot> buildAutoSelectSlotMap(
            Formation formation,
            List<SessionPlayer> lineup,
            boolean isAutoSelect) {
        if (formationService == null) {
            return Map.of();
        }
        FormationDefinition formationDto = formationService.getFormationByName(formation.getCode());
        if (formationDto == null || formationDto.positions() == null) {
            return Map.of();
        }
        Map<String, LineupSlot> slotMap = new HashMap<>();
        Set<String> usedPlayerIds = new HashSet<>();
        List<FormationPosition> positions = formationDto.positions();
        List<FormationPosition> assignmentPositions = isAutoSelect
            ? positions.stream()
                .sorted(Comparator.comparingInt(this::autoSelectAssignmentPriority))
                .toList()
            : positions;
        for (int positionIndex = 0; positionIndex < assignmentPositions.size(); positionIndex++) {
            FormationPosition pos = assignmentPositions.get(positionIndex);
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
                slotMap.put(subdivisionId, new LineupSlot(playerId, subdivisionId, null, null));
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
                        slotMap.put(subdivisionId, new LineupSlot(playerId, subdivisionId, null, null));
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
                            .thenComparingInt(player -> LineupRoleRules.tacticalFallbackScore(tacticalPositionGroupForRole(role), player.getPosition()))
                            .thenComparing(SessionPlayer::calculateOverall))
                    .ifPresent(player -> {
                        String playerId = player.getSessionPlayerId();
                        slotMap.put(subdivisionId, new LineupSlot(playerId, subdivisionId, null, null));
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
    private int autoSelectAssignmentPriority(FormationPosition position) {
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
            case "LW", "RW" -> LineupRoleRules.roleAwareSlotMatch(role, playerPosition, lineupHelper) || lineupHelper.isAttacker(playerPosition);
            case "CF", "ST" -> lineupHelper.isAttacker(playerPosition);
            default -> false;
        };
    }
    private boolean autoSelectSlotMatch(String role, String playerPosition) {
        return LineupRoleRules.roleAwareSlotMatch(role, playerPosition, lineupHelper) || categorySlotMatch(role, playerPosition);
    }
    private boolean shouldReserveCentralForwardForRemainingSlots(
            String currentRole,
            String playerPosition,
            List<SessionPlayer> lineup,
            Set<String> usedPlayerIds,
            List<FormationPosition> positions,
            int currentPositionIndex) {
        if (isCentralForwardRole(currentRole) || !LineupRoleRules.isCentralForwardPosition(playerPosition)) {
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
            .filter(player -> LineupRoleRules.isCentralForwardPosition(player.getPosition()))
            .count();
        return unusedCentralForwards <= remainingCentralForwardSlots;
    }
    private boolean shouldReserveWideFallbackForRemainingMidfieldSlots(
            String currentRole,
            String playerPosition,
            List<SessionPlayer> lineup,
            Set<String> usedPlayerIds,
            List<FormationPosition> positions,
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
        if (LineupRoleRules.roleAwareSlotMatch(role, playerPosition, lineupHelper)) {
            return 80;
        }
        if (categorySlotMatch(role, playerPosition)) {
            return 10;
        }
        return -100;
    }
    private int curatedRoleSlotBonus(SessionPlayer player, FormationPosition slot) {
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
    private String tacticalSlotSide(FormationPosition slot) {
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

