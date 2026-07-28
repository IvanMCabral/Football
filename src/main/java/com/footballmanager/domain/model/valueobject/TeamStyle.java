package com.footballmanager.domain.model.valueobject;

/**
 * Tactical style for match simulation.
 *
 * <p>This is a domain concept: it affects match probabilities, tactical
 * behavior and manager decisions, and is intentionally independent from web or
 * infrastructure concerns.
 */
public enum TeamStyle {

    /** No modifier; neutral tactical baseline. */
    BALANCED,

    /** Slightly higher attacking volume. */
    ATTACKING,

    /** Lower chance volume with defensive emphasis. */
    DEFENSIVE,

    /** Lower chance volume, better share when weaker than opponent. */
    COUNTER,

    /** Possession-oriented chance creation. */
    POSSESSION,

    /** Attack preference through wide channels / wings. */
    WIDE_PLAY,

    /** Attack preference through the left flank. */
    LEFT_FLANK,

    /** Attack preference through the right flank. */
    RIGHT_FLANK,

    /** Attack preference through the central lane. */
    CENTRAL_PLAY
}
