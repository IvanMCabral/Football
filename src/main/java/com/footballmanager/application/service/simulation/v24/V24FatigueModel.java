package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;

/**
 * V24C1: Pure function fatigue model for V24 detailed match engine.
 *
 * <p>Provides:
 * <ul>
 *   <li>Base stamina drain per minute (style-dependent)</li>
 *   <li>Extra drain for high-intensity actions (shot, foul, chance involvement)</li>
 *   <li>Fatigue factor [0.50, 1.00] from current stamina level</li>
 *   <li>Application of fatigue penalty to player quality scores</li>
 * </ul>
 *
 * <p>No state, no side effects, no Random, no Spring, no repository.
 * Deterministic from TeamStyle and V24PlayerMatchState.
 */
public final class V24FatigueModel {

    private static final int MIN_FATIGUE_FACTOR_BAND = 20;
    private static final int MAX_FATIGUE_FACTOR_BAND = 80;

    /**
     * Base stamina drain per minute of play (possession), style-dependent.
     */
    public int baseDrainPerMinute(TeamStyle style) {
        if (style == null) {
            return 5; // BALANCED default
        }
        return switch (style) {
            case ATTACKING -> 6;
            case POSSESSION -> 5;
            case COUNTER -> 5;
            case DEFENSIVE -> 4;
            case BALANCED -> 5;
        };
    }

    /**
     * Extra stamina drain for high-intensity actions.
     * Additive: shotAttempt=+8, foulCommitted=+5, chanceInvolved=+3.
     */
    public int actionDrain(boolean shotAttempt, boolean foulCommitted, boolean chanceInvolved) {
        int drain = 0;
        if (shotAttempt) drain += 8;
        if (foulCommitted) drain += 5;
        if (chanceInvolved) drain += 3;
        return drain;
    }

    /**
     * Apply stamina drain to a player, clamped to [0, 100].
     * Calls player.drainStamina(amount) which handles clamping internally.
     */
    public void applyDrain(V24PlayerMatchState player, int amount) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        if (amount <= 0) {
            return; // no drain for non-positive amount
        }
        player.drainStamina(amount);
    }

    /**
     * Fatigue factor from player's current stamina.
     * Returns [0.50, 1.00]: 1.0 = fresh, 0.5 = exhausted.
     */
    public double fatigueFactor(V24PlayerMatchState player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        int stamina = player.currentStamina();

        if (stamina >= 80) {
            return 1.00;
        } else if (stamina >= 60) {
            return 0.95;
        } else if (stamina >= 40) {
            return 0.85;
        } else if (stamina >= 20) {
            return 0.70;
        } else {
            return 0.50;
        }
    }

    /**
     * Apply fatigue penalty to a quality score.
     * Returns quality * fatigueFactor(player), clamped to [0.0, 1.0].
     * Preserves finite values — no NaN/Infinity.
     */
    public double applyFatigueToQuality(double quality, V24PlayerMatchState player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        if (!Double.isFinite(quality)) {
            return 0.0;
        }
        double factor = fatigueFactor(player);
        double result = quality * factor;
        // Clamp to [0.0, 1.0]
        if (result < 0.0) return 0.0;
        if (result > 1.0) return 1.0;
        return Math.round(result * 1000.0) / 1000.0;
    }

    /**
     * Full drain computation: base drain per minute + action drain.
     * Convenience method combining baseDrainPerMinute and actionDrain.
     */
    public int totalDrain(TeamStyle style, boolean shotAttempt, boolean foulCommitted, boolean chanceInvolved) {
        return baseDrainPerMinute(style) + actionDrain(shotAttempt, foulCommitted, chanceInvolved);
    }
}