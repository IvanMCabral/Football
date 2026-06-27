package com.footballmanager.domain.model.valueobject;

import java.util.Arrays;

/**
 * Formation - Formaciones tácticas con distribución de jugadores por rol.
 * Define la cantidad de defensores, mediocampistas y delanteros (GK siempre es 1).
 */
public enum Formation {
    FORMATION_4_4_2("4-4-2", 4, 4, 2),
    FORMATION_4_3_3("4-3-3", 4, 3, 3),
    FORMATION_4_2_3_1("4-2-3-1", 4, 5, 1), // 2 def mid + 3 att mid = 5
    FORMATION_3_5_2("3-5-2", 3, 5, 2),
    FORMATION_5_3_2("5-3-2", 5, 3, 2),
    FORMATION_4_1_4_1("4-1-4-1", 4, 5, 1),
    FORMATION_3_4_3("3-4-3", 3, 4, 3),
    // V25D54-C15 P1: 4 formations nuevas (feature requests de Iván)
    FORMATION_3_5_2_CDM("3-5-2-CDM", 3, 5, 2), // 3 CB + 1 CDM + 2 CM + 2 WB = 5 mids
    FORMATION_5_4_1("5-4-1", 5, 4, 1),
    FORMATION_3_4_1_2("3-4-1-2", 3, 5, 2),    // 3 CB + 4 MID + 1 CAM = 5 mids
    FORMATION_4_2_2_2("4-2-2-2", 4, 4, 2),     // 4 DEF + 2 CDM + 2 wide + 2 ST = 4 mids
    // V25D54-C15 P2: variante con pivote CDM
    FORMATION_4_3_3_1("4-3-3-1", 4, 3, 3);     // 4 DEF + 1 CDM + 2 CM + 3 ATT = 3 mids

    private final String displayName;
    private final int defenders;
    private final int midfielders;
    private final int attackers;

    Formation(String displayName, int defenders, int midfielders, int attackers) {
        this.displayName = displayName;
        this.defenders = defenders;
        this.midfielders = midfielders;
        this.attackers = attackers;
        
        // Validar que sume 10 (+ 1 GK = 11)
        if (defenders + midfielders + attackers != 10) {
            throw new IllegalStateException("Formation must have 10 outfield players (GK is always 1)");
        }
    }

    public String getDisplayName() {
        return displayName;
    }
    
    public int getDefenders() {
        return defenders;
    }
    
    public int getMidfielders() {
        return midfielders;
    }
    
    public int getAttackers() {
        return attackers;
    }
    
    public String getCode() {
        return displayName;
    }

    public static Formation ofDefault() {
        return FORMATION_4_3_3;
    }

    public static Formation fromString(String value) {
        return Arrays.stream(Formation.values())
                .filter(f -> f.name().equalsIgnoreCase(value) || f.displayName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown formation: " + value));
    }

    @Override
    public String toString() {
        return displayName;
    }
}

