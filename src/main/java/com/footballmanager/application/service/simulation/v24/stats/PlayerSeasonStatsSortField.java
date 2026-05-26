package com.footballmanager.application.service.simulation.v24.stats;

/**
 * V24D6M7: Sort fields exposed via the player season stats API.
 *
 * <p>Each field maps to a comparable property on PlayerSeasonStatsDto.
 * Unknown values are rejected with 400 Bad Request.
 */
public enum PlayerSeasonStatsSortField {
    GOALS("goals"),
    ASSISTS("assists"),
    AVERAGE_RATING("averageRating"),
    APPEARANCES("appearances"),
    STARTS("starts"),
    SHOTS("shots"),
    KEY_PASSES("keyPasses"),
    YELLOW_CARDS("yellowCards"),
    RED_CARDS("redCards"),
    INJURIES("injuries"),
    FOULS("fouls"),
    PLAYER_NAME("playerName");

    private final String dtoField;

    PlayerSeasonStatsSortField(String dtoField) {
        this.dtoField = dtoField;
    }

    public String dtoField() { return dtoField; }

    public static PlayerSeasonStatsSortField fromString(String value) {
        if (value == null) return null;
        for (PlayerSeasonStatsSortField f : values()) {
            if (f.name().equalsIgnoreCase(value) || f.dtoField.equalsIgnoreCase(value)) {
                return f;
            }
        }
        return null;
    }
}