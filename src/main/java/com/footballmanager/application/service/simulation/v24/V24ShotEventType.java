package com.footballmanager.application.service.simulation.v24;

/**
 * V25D33-F1: Shot origin taxonomy for xG modifier gating.
 *
 * <p>Some PlayerSkills only apply in specific shot contexts:
 * <ul>
 *   <li>{@link #HEADER} — only meaningful on shots that originate from a
 *       cross or corner (where the shooter is meeting the ball with their
 *       head, not striking with the foot). The V24 engine currently models
 *       {@link V24MatchEventType#CORNER} as a separate timeline event, but
 *       it does NOT trigger a follow-up shot — the corner roll happens
 *       AFTER the per-minute chance roll (see V24DetailedMatchEngine line
 *       412, after the shot attempt at line 337). Crosses are not modeled
 *       at all in V24 (no CROSS event type).</li>
 *   <li>{@link #OPEN_PLAY} — any other shot. Default for shots whose
 *       origin is not a set-piece delivery.</li>
 * </ul>
 *
 * <p>The {@code HEADER} multiplier ({@code 1.0 + skill/200.0}) is gated
 * on this enum in {@link V24ShotXgCalculator#calculateXg} so that
 * {@code HEADER=99} on a regular shot does NOT inflate xG — only on
 * shots whose origin is CORNER or CROSS.
 *
 * <p>This enum is internal to the V24 engine and the calculator tests.
 * It is NOT the same as {@link V24MatchEventType} (which is for timeline
 * events); it is an xG-pipeline parameter that gates skill multipliers.
 */
public enum V24ShotEventType {
    /** Regular open-play shot (default). No set-piece delivery. */
    OPEN_PLAY,
    /** Shot originating from a corner kick delivery. */
    CORNER,
    /** Shot originating from a cross (open-play delivery into the box). */
    CROSS
}