package com.footballmanager.domain.model;

import java.util.Arrays;

public enum Formation {
    FORMATION_4_4_2("4-4-2"),
    FORMATION_4_3_3("4-3-3"),
    FORMATION_4_2_3_1("4-2-3-1"),
    FORMATION_3_5_2("3-5-2"),
    FORMATION_5_3_2("5-3-2"),
    FORMATION_4_1_4_1("4-1-4-1"),
    FORMATION_3_4_3("3-4-3");

    private final String displayName;

    Formation(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
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
