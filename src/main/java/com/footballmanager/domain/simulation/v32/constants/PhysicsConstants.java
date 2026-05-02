package com.footballmanager.domain.simulation.v32.constants;

/**
 * Physics constants for V32 simulation.
 */
public final class PhysicsConstants {

    private PhysicsConstants() {}

    // ==================== FIELD DIMENSIONS ====================

    /** Field length in meters (touchline to touchline) */
    public static final float FIELD_LENGTH = 105.0f;

    /** Field width in meters (goal line to goal line) */
    public static final float FIELD_WIDTH = 68.0f;

    /** Goal width in meters */
    public static final float GOAL_WIDTH = 7.32f;

    /** Goal height in meters */
    public static final float GOAL_HEIGHT = 2.44f;

    /** Goal depth (distance behind goal line) */
    public static final float GOAL_DEPTH = 2.0f;

    /** Penalty area width in meters */
    public static final float PENALTY_AREA_WIDTH = 40.32f;

    /** Penalty area depth in meters */
    public static final float PENALTY_AREA_DEPTH = 16.5f;

    /** Center circle radius in meters */
    public static final float CENTER_CIRCLE_RADIUS = 9.15f;

    // ==================== BALL PHYSICS ====================

    /** Ball mass in kg */
    public static final float BALL_MASS = 0.43f;

    /** Ball radius in meters */
    public static final float BALL_RADIUS = 0.11f;

    /** Gravity acceleration in m/s^2 */
    public static final float GRAVITY = 9.81f;

    /** Maximum kick speed in m/s */
    public static final float MAX_KICK_SPEED = 35.0f;

    /** Long ball speed threshold */
    public static final float LONG_BALL_THRESHOLD = 25.0f;

    /** Ground friction coefficient */
    public static final float GROUND_FRICTION_COEFFICIENT = 0.35f;

    /** Air resistance coefficient */
    public static final double AIR_RESISTANCE_COEFFICIENT = 0.001;

    /** Bounce coefficient (energy retained on bounce) */
    public static final float BOUNCE_COEFFICIENT = 0.6f;

    /** Wall bounce coefficient */
    public static final float WALL_BOUNCE_COEFFICIENT = 0.7f;

    /** Magnus effect coefficient (spin-induced curve) */
    public static final double MAGNAL_COEFFICIENT = 0.001;

    /** Ball height above which it's considered airborne */
    public static final float AIR_BORNE_THRESHOLD = 0.3f;

    // ==================== PLAYER PHYSICS ====================

    /** Player collision radius in meters */
    public static final float PLAYER_RADIUS = 0.4f;

    /** Maximum player speed in m/s */
    public static final float MAX_PLAYER_SPEED = 8.0f;

    /** Maximum acceleration in m/s^2 */
    public static final float MAX_ACCELERATION = 4.5f;

    /** Maximum deceleration in m/s^2 */
    public static final float MAX_DECELERATION = 6.0f;

    /** Sprint speed threshold */
    public static final float SPRINT_THRESHOLD = 6.5f;

    /** Jog speed threshold */
    public static final float JOG_THRESHOLD = 3.0f;

    // ==================== SIMULATION TIMING ====================

    /** Ticks per second */
    public static final int TICKS_PER_SECOND = 60;

    /** Simulation tick duration in seconds */
    public static final double TICK_DURATION = 1.0 / TICKS_PER_SECOND;

    /** Minutes in a standard half */
    public static final int MINUTES_PER_HALF = 45;

    /** Ticks per half (45 minutes * 60 seconds * 60 ticks) */
    public static final long TICKS_PER_HALF = MINUTES_PER_HALF * 60L * TICKS_PER_SECOND;

    /** Ticks per match (90 minutes) */
    public static final long TICKS_PER_MATCH = 90L * 60L * TICKS_PER_SECOND;

    /** Extra time minutes per half */
    public static final int EXTRA_TIME_MINUTES = 15;

    /** Maximum extra time ticks */
    public static final long EXTRA_TIME_TICKS = EXTRA_TIME_MINUTES * 60L * TICKS_PER_SECOND;

    // ==================== POSITION BOUNDARIES ====================

    /** Coordinate origin is at center */
    /** Home goal X coordinate (negative, left side when viewed from home) */
    public static final float HOME_GOAL_X = -(FIELD_LENGTH / 2.0f);

    /** Away goal X coordinate (positive, right side) */
    public static final float AWAY_GOAL_X = FIELD_LENGTH / 2.0f;

    /** Minimum Y (field boundary) */
    public static final float FIELD_MIN_Y = -(FIELD_WIDTH / 2.0f);

    /** Maximum Y (field boundary) */
    public static final float FIELD_MAX_Y = FIELD_WIDTH / 2.0f;

    /** Center X */
    public static final float CENTER_X = 0.0f;

    /** Center Y */
    public static final float CENTER_Y = 0.0f;
}
