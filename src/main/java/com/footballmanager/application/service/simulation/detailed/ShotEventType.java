package com.footballmanager.application.service.simulation.detailed;

/**
 *
 * <p>Some PlayerSkills only apply in specific shot contexts:
 * <ul>
 *   <li>{@link #HEADER} — only meaningful on shots that originate from a
 *       cross or corner (where the shooter is meeting the ball with their
 *       head, not striking with the foot). The detailed match engine currently models
 *       {@link DetailedMatchEventType#CORNER} as a separate timeline event, but
 *       it does NOT trigger a follow-up shot — the corner roll happens
 *       412, after the shot attempt at line 337). Crosses are not modeled
 *       at all in detailed match (no CROSS event type).</li>
 *   <li>{@link #OPEN_PLAY} — any other shot. Default for shots whose
 *       origin is not a set-piece delivery.</li>
 * </ul>
 *
 * <p>The {@code HEADER} multiplier ({@code 1.0 + skill/200.0}) is gated
 * on this enum in {@link ShotXgCalculator#calculateXg} so that
 * {@code HEADER=99} on a regular shot does NOT inflate xG — only on
 * shots whose origin is CORNER or CROSS.
 *
 * <p>This enum is internal to the detailed match engine and the calculator tests.
 * It is NOT the same as {@link DetailedMatchEventType} (which is for timeline
 * events); it is an xG-pipeline parameter that gates skill multipliers.
 */
public enum ShotEventType {
    /** Regular open-play shot (default). No set-piece delivery. */
    OPEN_PLAY,
    /** Shot originating from a corner kick delivery. */
    CORNER,
    /** Shot originating from a cross (open-play delivery into the box). */
    CROSS
}
