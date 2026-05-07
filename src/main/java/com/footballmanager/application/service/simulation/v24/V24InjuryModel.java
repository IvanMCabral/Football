package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.Objects;
import java.util.Random;

/**
 * V24C3: Pure function injury model for V24 detailed match engine.
 *
 * <p>Provides:
 * <ul>
 *   <li>Base injury probability per minute/action check</li>
 *   <li>Stamina-based risk modulation</li>
 *   <li>High-intensity action risk modulation</li>
 *   <li>TeamStyle-based risk modulation</li>
 *   <li>Clamped probability range</li>
 * </ul>
 *
 * <p>No mutable state, no side effects. Deterministic via provided Random.
 * No Spring annotations, no repository dependencies.
 */
public final class V24InjuryModel {

    private static final double BASE_INJURY_PROB = 0.003;

    private static final double MIN_INJURY_PROB = 0.0005;
    private static final double MAX_INJURY_PROB = 0.02;

    public double baseInjuryProbability() {
        return BASE_INJURY_PROB;
    }

    public double adjustedInjuryProbability(
            V24PlayerMatchState player,
            TeamStyle style,
            boolean highIntensityAction) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        double prob = BASE_INJURY_PROB;

        // Stamina modifiers
        int stamina = player.currentStamina();
        if (stamina < 20) {
            prob += 0.008;
        } else if (stamina < 40) {
            prob += 0.004;
        }

        // High-intensity action modifier
        if (highIntensityAction) {
            prob += 0.002;
        }

        // Style modifiers
        if (style == null) style = TeamStyle.BALANCED;
        double styleMod = switch (style) {
            case ATTACKING -> 0.001;
            case COUNTER -> 0.001;
            case DEFENSIVE -> 0.0;
            case POSSESSION -> 0.0;
            case BALANCED -> 0.0;
        };
        prob += styleMod;

        return clamp(prob, MIN_INJURY_PROB, MAX_INJURY_PROB);
    }

    public boolean shouldInjure(
            V24PlayerMatchState player,
            TeamStyle style,
            boolean highIntensityAction,
            Random random) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        double prob = adjustedInjuryProbability(player, style, highIntensityAction);
        return random.nextDouble() < prob;
    }

    private double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}