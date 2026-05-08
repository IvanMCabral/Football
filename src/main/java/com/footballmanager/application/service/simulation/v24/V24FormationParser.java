package com.footballmanager.application.service.simulation.v24;

import java.util.Objects;

/**
 * V24D1: Formation parser for V24 detailed match engine.
 *
 * <p>Parses common formation strings into structured role counts.
 * Used for formation-aware tactical role weighting in player selection.
 *
 * <p>No mutable state, no side effects, no Spring, no repositories.
 */
public final class V24FormationParser {

    private static final V24Formation BALANCED_DEFAULT = parseUnchecked("4-4-2");

    public V24Formation parse(String formation) {
        if (formation == null || formation.isBlank()) {
            return BALANCED_DEFAULT;
        }
        String normalized = formation.trim().replaceAll("\\s+", "");
        // Normalize unicode em-dash to hyphen
        normalized = normalized.replace('–', '-');
        return parseUncheckedOrDefault(normalized);
    }

    /**
     * Internal parse without null/blank guard.
     */
    private static V24Formation parseUncheckedOrDefault(String formation) {
        V24Formation f = parseUnchecked(formation);
        if (f == null || f.outfieldPlayers() != 10) {
            return BALANCED_DEFAULT;
        }
        return f;
    }

    /**
     * Internal parser — returns null if unrecognized.
     */
    private static V24Formation parseUnchecked(String formation) {
        // 1 dash: "X-Y" — two lines (e.g. "4-4-2" splits as 4|4-2 → defenders=4, midfielders=4, forwards=2)
        int dashCount = countDashes(formation);
        if (dashCount == 1) {
            return parseOneDash(formation);
        }
        // 2 dashes: "X-Y-Z" — three lines
        if (dashCount == 2) {
            return parseTwoDashes(formation);
        }
        // 3 dashes: "X-Y-Z-W" — four lines (e.g. 4-2-3-1)
        if (dashCount == 3) {
            return parseThreeDashes(formation);
        }
        return null;
    }

    private static int countDashes(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '-') count++;
        }
        return count;
    }

    // "4-4-2" → defenders=4, midfielders=4, forwards=2
    private static V24Formation parseOneDash(String formation) {
        String[] parts = formation.split("-");
        if (parts.length != 2) return null;
        try {
            int first = Integer.parseInt(parts[0]);
            String second = parts[1]; // "Y-Z" style e.g. "4-2" → midfielders=4, forwards=2
            String[] secondParts = second.split("-");
            if (secondParts.length != 2) return null;
            int midfielders = Integer.parseInt(secondParts[0]);
            int forwards = Integer.parseInt(secondParts[1]);
            // Default: wingers=0, attackingMidfielders=0
            return new V24Formation(formation, first, midfielders, 0, 0, forwards);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // "4-3-3", "3-5-2" → three parts: defenders, midfielders, totalForwards
    private static V24Formation parseTwoDashes(String formation) {
        String[] parts = formation.split("-");
        if (parts.length != 3) return null;
        try {
            int def = Integer.parseInt(parts[0]);
            int mid = Integer.parseInt(parts[1]);
            int totalForwards = Integer.parseInt(parts[2]); // total forward line (includes wingers)
            // Standard 3-line formations with explicit winger slots
            if ("4-3-3".equals(formation)) {
                // 4-3-3: 4 defenders, 3 midfielders, 3 total forwards (2 wingers + 1 striker)
                return new V24Formation(formation, def, mid, 0, 2, totalForwards - 2);
            }
            if ("3-4-3".equals(formation)) {
                // 3-4-3: 3 defenders, 4 midfielders, 3 total forwards (2 wingers + 1 striker)
                return new V24Formation(formation, def, mid, 0, 2, totalForwards - 2);
            }
            if ("3-5-2".equals(formation)) {
                // 3-5-2: 3 defenders, 5 midfielders, 2 forwards (no separate wingers)
                return new V24Formation(formation, def, mid, 0, 0, totalForwards);
            }
            if ("5-3-2".equals(formation)) {
                // 5-3-2: 5 defenders, 3 midfielders, 2 forwards (no wingers)
                return new V24Formation(formation, def, mid, 0, 0, totalForwards);
            }
            // Generic: no separate winger count
            return new V24Formation(formation, def, mid, 0, 0, totalForwards);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // "4-2-3-1" → four parts: defenders, midfielders, attackingMidfielders, forwards
    private static V24Formation parseThreeDashes(String formation) {
        String[] parts = formation.split("-");
        if (parts.length != 4) return null;
        try {
            int def = Integer.parseInt(parts[0]);
            int mid = Integer.parseInt(parts[1]);
            int am = Integer.parseInt(parts[2]);
            int fwd = Integer.parseInt(parts[3]);
            // 4-2-3-1: X-Y-Z-W → defenders=X, midfielders=Y+Z, forwards=W (no separate wingers)
            if ("4-2-3-1".equals(formation)) {
                int totalMid = mid + am; // 2+3=5
                return new V24Formation(formation, def, totalMid, 0, 0, fwd);
            }
            // 4-1-4-1: X-Y-Z-W → defenders=X, midfielders=Y+Z, forwards=W (no separate wingers)
            if ("4-1-4-1".equals(formation)) {
                int totalMid = mid + am; // 1+4=5
                return new V24Formation(formation, def, totalMid, 0, 0, fwd);
            }
            // Generic 4-line formation: no separate wingers
            return new V24Formation(formation, def, mid, am, 0, fwd);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Immutable formation structure.
     */
    public static final class V24Formation {
        private final String raw;
        private final int defenders;
        private final int midfielders;
        private final int attackingMidfielders;
        private final int wingers;
        private final int forwards;

        public V24Formation(String raw, int defenders, int midfielders,
                            int attackingMidfielders, int wingers, int forwards) {
            this.raw = raw;
            this.defenders = defenders;
            this.midfielders = midfielders;
            this.attackingMidfielders = attackingMidfielders;
            this.wingers = wingers;
            this.forwards = forwards;
        }

        public String raw() { return raw; }
        public int defenders() { return defenders; }
        public int midfielders() { return midfielders; }
        public int attackingMidfielders() { return attackingMidfielders; }
        public int wingers() { return wingers; }
        public int forwards() { return forwards; }

        /** Total non-GK players = defenders + midfielders + attackingMidfielders + wingers + forwards */
        public int outfieldPlayers() {
            return defenders + midfielders + attackingMidfielders + wingers + forwards;
        }

        public boolean hasWingers() { return wingers > 0; }
        public boolean hasSingleStriker() { return forwards == 1; }
        public boolean hasTwoStrikers() { return forwards == 2; }

        public boolean isBackThree() { return defenders == 3; }
        public boolean isBackFour() { return defenders == 4; }
        public boolean isBackFive() { return defenders == 5; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof V24Formation that)) return false;
            return Objects.equals(raw, that.raw);
        }

        @Override
        public int hashCode() {
            return Objects.hash(raw);
        }

        @Override
        public String toString() {
            return "V24Formation{raw='" + raw + "', def=" + defenders
                    + ", mid=" + midfielders + ", am=" + attackingMidfielders
                    + ", wing=" + wingers + ", fwd=" + forwards + "}";
        }
    }
}