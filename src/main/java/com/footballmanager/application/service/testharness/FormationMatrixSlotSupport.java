package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.service.editor.FormationPosition;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.entity.SessionPlayer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class FormationMatrixSlotSupport {

    private FormationMatrixSlotSupport() {
    }

    static Map<String, LineupSlot> buildSlots(List<SessionPlayer> starters, FormationDefinition formation) {
        List<FormationPosition> positions = formation.positions().stream()
            .sorted(Comparator.comparing(FormationPosition::index))
            .toList();
        if (positions.size() != starters.size()) {
            throw new IllegalStateException(
                "Formation " + formation.name() + " has " + positions.size()
                    + " positions for " + starters.size() + " starters");
        }

        List<SessionPlayer> remaining = new ArrayList<>(starters);
        Map<String, LineupSlot> slots = new LinkedHashMap<>();
        for (FormationPosition position : positions) {
            SessionPlayer player = pickBestPlayerForFormationPosition(remaining, position);
            if (player == null) {
                throw new IllegalStateException(
                    "Could not assign player to formation " + formation.name()
                        + " position " + position.role());
            }
            remaining.remove(player);
            slots.put(player.getSessionPlayerId(), new LineupSlot(
                player.getSessionPlayerId(),
                position.subdivisionId(),
                position.xPercent(),
                position.yPercent()));
        }
        return slots;
    }

    static int fitScore(SessionPlayer player, FormationPosition position) {
        String playerProfile = playerProfile(player);
        String slotProfile = slotProfile(position != null ? position.role() : null);
        int baseScore;
        if (playerProfile.equals(slotProfile)) baseScore = 100;
        else if ("WIDE_DEF".equals(slotProfile) && "DEF".equals(playerProfile)) baseScore = 92;
        else if ("DEF".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) baseScore = 90;
        else if ("WIDE_ATT".equals(slotProfile) && "ATT".equals(playerProfile)) baseScore = 88;
        else if ("ATT".equals(slotProfile) && "WIDE_ATT".equals(playerProfile)) baseScore = 86;
        else if ("AM".equals(slotProfile) && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile))) baseScore = 82;
        else if ("MID".equals(slotProfile) && ("DM".equals(playerProfile) || "AM".equals(playerProfile))) baseScore = 80;
        else if ("DM".equals(slotProfile) && ("MID".equals(playerProfile) || "DEF".equals(playerProfile))) baseScore = 78;
        else if ("WIDE_MID".equals(slotProfile) && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile) || "WIDE_DEF".equals(playerProfile))) baseScore = 76;
        else if ("MID".equals(slotProfile) && "WIDE_MID".equals(playerProfile)) baseScore = 74;
        else if ("ATT".equals(slotProfile) && "AM".equals(playerProfile)) baseScore = 70;
        else if ("AM".equals(slotProfile) && "ATT".equals(playerProfile)) baseScore = 68;
        else if ("DEF".equals(slotProfile) && "DM".equals(playerProfile)) baseScore = 66;
        else if ("DM".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) baseScore = 62;
        else if ("MID".equals(slotProfile) && ("DEF".equals(playerProfile) || "ATT".equals(playerProfile))) baseScore = 52;
        else if ("DEF".equals(slotProfile) && "MID".equals(playerProfile)) baseScore = 48;
        else if ("ATT".equals(slotProfile) && "MID".equals(playerProfile)) baseScore = 48;
        else if ("GK".equals(slotProfile) || "GK".equals(playerProfile)) baseScore = 0;
        else baseScore = 35;
        return baseScore + curatedSlotBonus(player, position);
    }

    static int playerStrength(SessionPlayer player) {
        if (player == null) return 0;
        String profile = playerProfile(player);
        return switch (profile) {
            case "GK" -> player.getDefense() + player.getMentality();
            case "DEF", "WIDE_DEF" -> player.getDefense() * 2 + player.getMentality() + player.getStamina();
            case "MID", "DM", "AM", "WIDE_MID" -> player.getTechnique() * 2 + player.getMentality() + player.getStamina();
            case "ATT", "WIDE_ATT" -> player.getAttack() * 2 + player.getTechnique() + player.getSpeed();
            default -> player.getAttack() + player.getDefense() + player.getTechnique() + player.getSpeed();
        };
    }

    private static SessionPlayer pickBestPlayerForFormationPosition(
        List<SessionPlayer> candidates,
        FormationPosition position) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
            .max(Comparator
                .comparingInt((SessionPlayer player) -> fitScore(player, position))
                .thenComparingInt(FormationMatrixSlotSupport::playerStrength))
            .orElse(null);
    }

    private static int curatedSlotBonus(SessionPlayer player, FormationPosition slot) {
        CuratedMatrixRoleProfile profile = curatedRoleProfile(player);
        if (profile == null || slot == null || slot.role() == null) {
            return 0;
        }
        String role = slot.role().toUpperCase(Locale.ROOT);
        int bonus = 0;
        if (profile.roles().contains(role)) {
            bonus += 26;
        } else if (roleFamilyMatch(profile.roles(), role)) {
            bonus += 12;
        }
        String slotSide = slotSide(slot);
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

    private static boolean roleFamilyMatch(Set<String> playerRoles, String slotRole) {
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

    private static String slotSide(FormationPosition slot) {
        String role = slot.role() != null ? slot.role().toUpperCase(Locale.ROOT) : "";
        if (Set.of("LB", "LWB", "LM", "LW").contains(role)) return "LEFT";
        if (Set.of("RB", "RWB", "RM", "RW").contains(role)) return "RIGHT";
        if (Set.of("GK", "CB", "CDM", "CM", "ST", "CF").contains(role)) return "CENTER";
        Double x = slot.xPercent();
        if (x != null && x <= 42) return "LEFT";
        if (x != null && x >= 58) return "RIGHT";
        return "CENTER";
    }

    private static String oppositeSide(String side) {
        return "LEFT".equals(side) ? "RIGHT" : "LEFT";
    }

    private static CuratedMatrixRoleProfile curatedRoleProfile(SessionPlayer player) {
        if (player == null || player.getName() == null) {
            return null;
        }
        return switch (normalizePlayerName(player.getName())) {
            case "dani carvajal" -> new CuratedMatrixRoleProfile(Set.of("RB", "RWB"), Set.of("RIGHT"));
            case "david alaba" -> new CuratedMatrixRoleProfile(Set.of("CB", "LB"), Set.of("LEFT", "CENTER"));
            case "ferland mendy", "fran garcia" -> new CuratedMatrixRoleProfile(Set.of("LB", "LWB"), Set.of("LEFT"));
            case "lucas vazquez" -> new CuratedMatrixRoleProfile(Set.of("RB", "RM", "RWB"), Set.of("RIGHT"));
            case "vinicius junior" -> new CuratedMatrixRoleProfile(Set.of("LW", "LM"), Set.of("LEFT"));
            case "rodrygo goes" -> new CuratedMatrixRoleProfile(Set.of("RW", "LW", "ST", "CF"), Set.of("RIGHT", "BOTH"));
            case "brahim diaz" -> new CuratedMatrixRoleProfile(Set.of("RW", "CAM", "RM"), Set.of("RIGHT", "CENTER"));
            case "federico valverde" -> new CuratedMatrixRoleProfile(Set.of("CM", "RM", "CDM"), Set.of("CENTER", "RIGHT"));
            case "eduardo camavinga" -> new CuratedMatrixRoleProfile(Set.of("CM", "CDM", "LB"), Set.of("CENTER", "LEFT"));
            default -> null;
        };
    }

    private static String normalizePlayerName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    private static String playerProfile(SessionPlayer player) {
        if (player == null || player.getPosition() == null) return "MID";
        String position = player.getPosition().toUpperCase(Locale.ROOT);
        return switch (position) {
            case "GK" -> "GK";
            case "DEF" -> "DEF";
            case "MID" -> "MID";
            case "WINGER" -> "WIDE_ATT";
            case "ATT" -> "ATT";
            default -> "MID";
        };
    }

    private static String slotProfile(String role) {
        if (role == null || role.isBlank()) return "MID";
        String normalized = role.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GK" -> "GK";
            case "LB", "RB", "LWB", "RWB" -> "WIDE_DEF";
            case "CB" -> "DEF";
            case "CDM", "DM" -> "DM";
            case "LM", "RM" -> "WIDE_MID";
            case "CAM", "AM" -> "AM";
            case "LW", "RW" -> "WIDE_ATT";
            case "ST", "CF" -> "ATT";
            default -> "MID";
        };
    }

    private record CuratedMatrixRoleProfile(Set<String> roles, Set<String> sides) {
    }
}

