package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.LineupSlot;

import java.util.Map;

final class V24TacticalPositionService {

    double tacticalYPercent(V24PlayerMatchState player, Map<String, LineupSlot> slotsByPlayerId) {
        LineupSlot slot = slotFor(player, slotsByPlayerId);
        if (slot != null && slot.customYPercent() != null && Double.isFinite(slot.customYPercent())) {
            return clamp(slot.customYPercent(), 0.0, 100.0);
        }
        Double canonical = canonicalYPercent(slot);
        if (canonical != null) {
            return canonical;
        }
        return switch (player.position()) {
            case "ATT", "WINGER" -> 15.0;
            case "MID" -> 50.0;
            case "GK" -> 92.0;
            default -> 78.0;
        };
    }

    double tacticalXPercent(V24PlayerMatchState player, Map<String, LineupSlot> slotsByPlayerId) {
        LineupSlot slot = slotFor(player, slotsByPlayerId);
        if (slot != null && slot.customXPercent() != null && Double.isFinite(slot.customXPercent())) {
            return clamp(slot.customXPercent(), 0.0, 100.0);
        }
        Double canonical = canonicalXPercent(slot);
        if (canonical != null) {
            return canonical;
        }
        return switch (player.position()) {
            case "WINGER" -> 18.0;
            default -> 50.0;
        };
    }

    LineupSlot slotFor(V24PlayerMatchState player, Map<String, LineupSlot> slotsByPlayerId) {
        if (player == null || slotsByPlayerId == null || slotsByPlayerId.isEmpty()) {
            return null;
        }
        return slotsByPlayerId.get(player.sessionPlayerId());
    }

    double laneLeftWeight(double xPercent) {
        return clamp((50.0 - xPercent) / 30.0, 0.0, 1.0);
    }

    double laneRightWeight(double xPercent) {
        return clamp((xPercent - 50.0) / 30.0, 0.0, 1.0);
    }

    double laneCenterWeight(double xPercent) {
        return 1.0 - Math.max(laneLeftWeight(xPercent), laneRightWeight(xPercent));
    }

    private Double canonicalXPercent(LineupSlot slot) {
        int[] parsed = parseSubdivision(slot);
        if (parsed == null) {
            return null;
        }
        int sector = parsed[0];
        int subIndex = parsed[1];
        int sectorCol = (sector - 1) % 3;
        double left = (sectorCol * 3 + (subIndex - 1)) * 11.11;
        return clamp(left + 11.11 / 2.0, 0.0, 100.0);
    }

    private Double canonicalYPercent(LineupSlot slot) {
        if (slot != null && "GK-1".equals(slot.subdivisionId())) {
            return 93.0;
        }
        int[] parsed = parseSubdivision(slot);
        if (parsed == null) {
            return null;
        }
        int sector = parsed[0];
        int sectorRow = (sector - 1) / 3;
        double top = sectorRow * 11.11;
        return clamp(top + 11.11 / 2.0, 0.0, 100.0);
    }

    private int[] parseSubdivision(LineupSlot slot) {
        if (slot == null || slot.subdivisionId() == null) {
            return null;
        }
        String id = slot.subdivisionId();
        if ("GK-1".equals(id) || !id.startsWith("S")) {
            return null;
        }
        int dash = id.indexOf('-');
        if (dash < 0 || dash >= id.length() - 1) {
            return null;
        }
        try {
            int sector = Integer.parseInt(id.substring(1, dash));
            int subIndex = Integer.parseInt(id.substring(dash + 1));
            if (sector < 1 || sector > 27 || subIndex < 1 || subIndex > 3) {
                return null;
            }
            return new int[] { sector, subIndex };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
