package com.footballmanager.domain.simulation.v32.enums;

/**
 * Match phases for V32 simulation.
 * Used by the phase engine to determine play style and AI behavior.
 */
public enum MatchPhase {
    /** Pre-match */
    PRE_MATCH(0),
    /** Kickoff - starting phase */
    KICKOFF(1),
    /** Building up play from defense */
    BUILD_UP(2),
    /** Attacking progression */
    ATTACKING(3),
    /** Chance creation */
    CHANCE(4),
    /** Shot on goal */
    SHOT(5),
    /** Defensive transition */
    DEFENSIVE_TRANSITION(6),
    /** Attacking transition */
    ATTACKING_TRANSITION(7),
    /** Counter attack */
    COUNTER_ATTACK(8),
    /** Set piece (corner, free kick, throw-in) */
    SET_PIECE(9),
    /** Penalty */
    PENALTY(10),
    /** Goal celebration */
    GOAL_CELEBRATION(11),
    /** Half time */
    HALF_TIME(12),
    /** Extra time */
    EXTRA_TIME(13),
    /** Full time */
    FULL_TIME(14),
    /** Match ended */
    MATCH_OVER(15);

    private final byte id;

    MatchPhase(int id) {
        this.id = (byte) id;
    }

    public byte getId() { return id; }

    public static MatchPhase fromId(int id) {
        for (MatchPhase phase : values()) {
            if (phase.id == id) return phase;
        }
        return BUILD_UP;
    }

    /** @return true if this is an attacking phase */
    public boolean isAttacking() {
        return this == ATTACKING || this == CHANCE || this == SHOT ||
               this == COUNTER_ATTACK || this == SET_PIECE;
    }

    /** @return true if this is a defensive phase */
    public boolean isDefensive() {
        return this == DEFENSIVE_TRANSITION;
    }

    /** @return true if match is actively playing */
    public boolean isPlaying() {
        return this != PRE_MATCH && this != HALF_TIME &&
               this != FULL_TIME && this != MATCH_OVER &&
               this != GOAL_CELEBRATION;
    }
}
