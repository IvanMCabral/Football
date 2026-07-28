package com.footballmanager.application.service.lineup;

import java.util.Locale;

final class LineupRoleRules {

    private LineupRoleRules() {
    }

    static int tacticalFallbackScore(String positionGroup, String playerPosition) {
        if (positionGroup == null || playerPosition == null) {
            return 0;
        }
        String group = positionGroup.toUpperCase(Locale.ROOT);
        String pos = playerPosition.toUpperCase(Locale.ROOT);
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

    static boolean isCentralForwardPosition(String position) {
        if (position == null) {
            return false;
        }
        return switch (position.toUpperCase(Locale.ROOT)) {
            case "ATT", "ST", "CF" -> true;
            default -> false;
        };
    }

    static boolean isWideAttackingNatural(String playerPosition) {
        if (playerPosition == null) {
            return false;
        }
        return switch (playerPosition.toUpperCase(Locale.ROOT)) {
            case "WINGER", "LW", "RW", "LM", "RM" -> true;
            default -> false;
        };
    }

    static boolean isWideMidfieldNatural(String playerPosition) {
        if (playerPosition == null) {
            return false;
        }
        return switch (playerPosition.toUpperCase(Locale.ROOT)) {
            case "WINGER", "LW", "RW", "LM", "RM", "LWB", "RWB", "LB", "RB" -> true;
            default -> false;
        };
    }

    static boolean isDedicatedWideMidfieldNatural(String playerPosition) {
        if (playerPosition == null) {
            return false;
        }
        return switch (playerPosition.toUpperCase(Locale.ROOT)) {
            case "LM", "RM", "LWB", "RWB", "LB", "RB" -> true;
            default -> false;
        };
    }

    static boolean roleAwareSlotMatch(String role, String playerPosition, LineupHelper lineupHelper) {
        if (role == null || playerPosition == null) {
            return false;
        }
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "GK" -> "GK".equalsIgnoreCase(playerPosition);
            case "ST", "CF" -> isCentralForwardPosition(playerPosition);
            case "LW", "RW" -> isWideAttackingNatural(playerPosition);
            case "LM", "RM", "LWB", "RWB" -> isWideMidfieldNatural(playerPosition);
            case "CDM", "CM", "CAM" -> lineupHelper.isMidfielder(playerPosition);
            case "LB", "CB", "RB" -> lineupHelper.isDefender(playerPosition);
            default -> role.equalsIgnoreCase(playerPosition);
        };
    }
}
