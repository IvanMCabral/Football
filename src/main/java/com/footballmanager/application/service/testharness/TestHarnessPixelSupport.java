package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.Locale;
import java.util.Optional;

final class TestHarnessPixelSupport {

    private TestHarnessPixelSupport() {
    }

    static PositionPixelPlayerDiagnostic diagnostic(
            SessionPlayer player,
            String slotId,
            double xPercent,
            double yPercent) {
        String natural = autoLine(player);
        String tactical = tacticalPositionFromPixel(slotId, yPercent, natural);
        double effectiveness = com.footballmanager.domain.model.valueobject.SubdivisionEffectivenessCalculator
            .effectiveness(natural, xPercent, yPercent, tactical);
        int attack = TestHarnessCommonSupport.safeInt(player.getAttack());
        int defense = TestHarnessCommonSupport.safeInt(player.getDefense());
        int mentality = TestHarnessCommonSupport.safeInt(player.getMentality());
        double baseCollective = "GK".equals(natural)
            ? defense * 0.75 + mentality * 0.25
            : switch (tactical) {
                case "ATT" -> attack * 0.65 + TestHarnessCommonSupport.safeInt(player.getTechnique()) * 0.2 + mentality * 0.15;
                case "DEF" -> defense * 0.65 + mentality * 0.25 + TestHarnessCommonSupport.safeInt(player.getStamina()) * 0.10;
                default -> TestHarnessCommonSupport.safeInt(player.getTechnique()) * 0.45
                    + mentality * 0.30
                    + (attack + defense) * 0.125;
            };
        return new PositionPixelPlayerDiagnostic(
            tactical,
            TestHarnessCommonSupport.round3(effectiveness),
            TestHarnessCommonSupport.round2(baseCollective * effectiveness));
    }

    static String tacticalPositionFromPixel(String slotId, double yPercent, String naturalLine) {
        if ("GK".equals(naturalLine) || "GK-1".equals(slotId)) {
            return "GK";
        }
        if (yPercent <= 32.0) {
            return "ATT";
        }
        if (yPercent >= 68.0) {
            return "DEF";
        }
        return "MID";
    }

    static String autoLine(SessionPlayer player) {
        if (player == null || player.getPosition() == null) {
            return "MID";
        }
        return autoLine(player.getPosition());
    }

    static String autoLine(String rawPosition) {
        if (rawPosition == null || rawPosition.isBlank()) {
            return "MID";
        }
        String position = rawPosition.toUpperCase(Locale.ROOT);
        return switch (position) {
            case "GK" -> "GK";
            case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> "DEF";
            case "ATT", "ST", "CF", "LW", "RW", "WINGER" -> "ATT";
            default -> "MID";
        };
    }

    static double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    static double fallbackYPercent(String position) {
        String normalized = position != null ? position.toUpperCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "GK" -> 94.0;
            case "RB", "RWB", "LB", "LWB", "CB", "DEF" -> 78.0;
            case "DM", "CDM" -> 66.0;
            case "CM", "MID", "LM", "RM" -> 52.0;
            case "AM", "CAM" -> 38.0;
            case "ST", "CF", "LW", "RW", "ATT", "WINGER" -> 18.0;
            default -> 52.0;
        };
    }

    static Optional<Double> canonicalXPercent(String subdivisionId) {
        int[] parsed = parseSubdivision(subdivisionId);
        if (parsed == null) {
            return Optional.empty();
        }
        int sector = parsed[0];
        int subIndex = parsed[1];
        int sectorCol = (sector - 1) % 3;
        double left = (sectorCol * 3 + (subIndex - 1)) * 11.11;
        return Optional.of(clampPercent(left + 11.11 / 2.0));
    }

    static Optional<Double> canonicalYPercent(String subdivisionId) {
        if ("GK-1".equals(subdivisionId)) {
            return Optional.of(93.0);
        }
        int[] parsed = parseSubdivision(subdivisionId);
        if (parsed == null) {
            return Optional.empty();
        }
        int sector = parsed[0];
        int sectorRow = (sector - 1) / 3;
        double top = sectorRow * 11.11;
        return Optional.of(clampPercent(top + 11.11 / 2.0));
    }

    private static int[] parseSubdivision(String subdivisionId) {
        if (subdivisionId == null || !subdivisionId.startsWith("S")) {
            return null;
        }
        int dash = subdivisionId.indexOf('-');
        if (dash < 0 || dash >= subdivisionId.length() - 1) {
            return null;
        }
        try {
            int sector = Integer.parseInt(subdivisionId.substring(1, dash));
            int subIndex = Integer.parseInt(subdivisionId.substring(dash + 1));
            if (sector < 1 || sector > 27 || subIndex < 1 || subIndex > 3) {
                return null;
            }
            return new int[] { sector, subIndex };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
