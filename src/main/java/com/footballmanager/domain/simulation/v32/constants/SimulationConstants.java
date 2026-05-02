package com.footballmanager.domain.simulation.v32.constants;

/**
 * Simulation-wide constants for V32 engine.
 */
public final class SimulationConstants {

    private SimulationConstants() {}

    // ==================== MATCH CONFIGURATION ====================

    /** Enable deterministic replay */
    public static final boolean DETERMINISTIC_REPLAY = true;

    /** Enable state history for rollback */
    public static final boolean STATE_HISTORY_ENABLED = true;

    /** Maximum history entries to keep */
    public static final int MAX_HISTORY_ENTRIES = 9000; // ~2.5 minutes at 60tps

    /** History save interval (every N ticks) */
    public static final int HISTORY_SAVE_INTERVAL = 15; // ~4 times per second

    // ==================== RNG STREAMS ====================

    /** Number of RNG streams */
    public static final int RNG_STREAM_COUNT = 5;

    /** RNG stream: Physics (ball, collisions) */
    public static final int STREAM_PHYSICS = 0;

    /** RNG stream: AI decisions */
    public static final int STREAM_AI = 1;

    /** RNG stream: Event sampling (fouls, cards) */
    public static final int STREAM_EVENT = 2;

    /** RNG stream: Shot outcomes */
    public static final int STREAM_SHOT = 3;

    /** RNG stream: Meta (fatigue, morale) */
    public static final int STREAM_META = 4;

    // ==================== TACTICAL AI ====================

    /** Minimum decision interval in ticks */
    public static final int MIN_DECISION_INTERVAL = 15; // 250ms

    /** Maximum decision interval in ticks */
    public static final int MAX_DECISION_INTERVAL = 45; // 750ms

    /** Decision inertia duration in ticks */
    public static final int DECISION_INERTIA_TICKS = 30; // 500ms

    /** AI reaction delay in ticks */
    public static final int AI_REACTION_DELAY = 20; // ~333ms

    // ==================== PLAYER AI ====================

    /** Distance to consider ball loose */
    public static final float LOOSE_BALL_DISTANCE = 8.0f;

    /** Distance to trigger pressing */
    public static final float PRESSING_TRIGGER_DISTANCE = 5.0f;

    /** Distance to trigger tracking back */
    public static final float TRACKING_DISTANCE = 12.0f;

    /** Marking distance threshold */
    public static final float MARKING_DISTANCE = 2.5f;

    /** Optimal passing distance */
    public static final float OPTIMAL_PASS_DISTANCE = 15.0f;

    /** Long pass distance threshold */
    public static final float LONG_PASS_DISTANCE = 30.0f;

    // ==================== xG MODEL ====================

    /** Base xG value */
    public static final double XG_BASE = 0.05;

    /** xG distance coefficient */
    public static final double XG_DISTANCE_COEFFICIENT = 0.05;

    /** xG angle coefficient */
    public static final double XG_ANGLE_COEFFICIENT = 0.2;

    /** xG one-on-one bonus */
    public static final double XG_ONE_ON_ONE = 0.15;

    /** xG header bonus */
    public static final double XG_HEADER_BONUS = -0.1;

    /** xG penalty */
    public static final double XG_PENALTY = 0.75;

    /** xG free kick */
    public static final double XG_FREE_KICK = 0.08;

    /** xG corner */
    public static final double XG_CORNER = 0.05;

    // ==================== GOALKEEPER ====================

    /** Goalkeeper reach in meters */
    public static final float GK_REACH = 1.8f;

    /** Goalkeeper dive speed */
    public static final float GK_DIVE_SPEED = 6.0f;

    /** Goalkeeper reaction time */
    public static final float GK_REACTION_TIME = 0.15f;

    /** Shot on target probability base */
    public static final float SHOT_ON_TARGET_BASE = 0.35f;

    /** Cross claim probability */
    public static final float CROSS_CLAIM_PROBABILITY = 0.25f;

    // ==================== FATIGUE MODEL ====================

    /** Fatigue accumulation rate per tick (running) */
    public static final float FATIGUE_ACCUMULATION_RATE = 0.0002f;

    /** Fatigue recovery rate per tick (resting) */
    public static final float FATIGUE_RECOVERY_RATE = 0.0001f;

    /** Stamina threshold for performance drop */
    public static final float STAMINA_THRESHOLD = 0.3f;

    /** Low stamina speed penalty */
    public static final float LOW_STAMINA_PENALTY = 0.25f;

    /** Low stamina decision penalty */
    public static final float LOW_STAMINA_DECISION_PENALTY = 0.15f;

    // ==================== HUMAN ERROR ====================

    /** First touch error probability base */
    public static final float FIRST_TOUCH_ERROR_BASE = 0.08f;

    /** Misplaced pass probability base */
    public static final float MISPLACED_PASS_BASE = 0.12f;

    /** Slip probability (wet field) */
    public static final float SLIP_PROBABILITY = 0.02f;

    /** Heavy touch probability */
    public static final float HEAVY_TOUCH_PROBABILITY = 0.15f;

    /** Error magnitude multiplier */
    public static final float ERROR_MAGNITUDE = 2.0f;

    // ==================== MOMENTUM ====================

    /** Momentum change rate */
    public static final float MOMENTUM_CHANGE_RATE = 0.02f;

    /** Goal momentum swing */
    public static final float GOAL_MOMENTUM_SWING = 0.3f;

    /** Missed chance momentum impact */
    public static final float MISSED_CHANCE_IMPACT = 0.1f;

    /** Momentum decay per tick */
    public static final float MOMENTUM_DECAY = 0.001f;

    // ==================== VALIDATION ====================

    /** Enable extended validation in debug */
    public static final boolean EXTENDED_VALIDATION = true;

    /** Validation interval in ticks */
    public static final int VALIDATION_INTERVAL = 30;

    /** Maximum velocity sanity check */
    public static final float MAX_VELOCITY_SANITY = 50.0f;

    /** Maximum position delta sanity check */
    public static final float MAX_POSITIVE_SANITY = 10.0f;
}
