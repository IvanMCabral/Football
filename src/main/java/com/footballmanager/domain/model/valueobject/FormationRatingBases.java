package com.footballmanager.domain.model.valueobject;

import java.util.Map;
import java.util.Set;

final class FormationRatingBases {

    private static final Map<String, Double> ATTACK = Map.ofEntries(
            Map.entry("4-4-2", 1.00),
            Map.entry("4-3-3", 1.18),
            Map.entry("4-2-3-1", 1.16),
            Map.entry("3-4-3", 1.14),
            Map.entry("3-5-2", 0.90),
            Map.entry("5-3-2", 0.82),
            Map.entry("4-1-4-1", 0.88),
            Map.entry("3-5-2-CDM", 0.84),
            Map.entry("5-4-1", 0.76),
            Map.entry("3-4-1-2", 1.06),
            Map.entry("4-2-2-2", 1.08),
            Map.entry("4-1-2-3", 1.14)
    );

    private static final Map<String, Double> MIDFIELD = Map.ofEntries(
            Map.entry("4-4-2", 1.00),
            Map.entry("4-3-3", 0.95),
            Map.entry("4-2-3-1", 1.02),
            Map.entry("3-4-3", 0.98),
            Map.entry("3-5-2", 1.05),
            Map.entry("5-3-2", 0.98),
            Map.entry("4-1-4-1", 1.04),
            Map.entry("3-5-2-CDM", 1.04),
            Map.entry("5-4-1", 1.02),
            Map.entry("3-4-1-2", 1.00),
            Map.entry("4-2-2-2", 0.96),
            Map.entry("4-1-2-3", 0.98)
    );

    private static final Map<String, Double> DEFENSE = Map.ofEntries(
            Map.entry("4-4-2", 1.00),
            Map.entry("4-3-3", 0.87),
            Map.entry("4-2-3-1", 0.82),
            Map.entry("3-4-3", 0.88),
            Map.entry("3-5-2", 1.05),
            Map.entry("5-3-2", 1.20),
            Map.entry("4-1-4-1", 1.10),
            Map.entry("3-5-2-CDM", 1.12),
            Map.entry("5-4-1", 1.22),
            Map.entry("3-4-1-2", 0.94),
            Map.entry("4-2-2-2", 0.96),
            Map.entry("4-1-2-3", 0.88)
    );

    private FormationRatingBases() {
    }

    static Set<String> formations() {
        return ATTACK.keySet();
    }

    static double attack(String formation) {
        return ATTACK.getOrDefault(formation, 1.00);
    }

    static double midfield(String formation) {
        return MIDFIELD.getOrDefault(formation, 1.00);
    }

    static double defense(String formation) {
        return DEFENSE.getOrDefault(formation, 1.00);
    }
}
