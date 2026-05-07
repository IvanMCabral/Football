package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.Objects;
import java.util.Random;

/**
 * V24C2: Pure function discipline/foul card model for V24 detailed match engine.
 *
 * <p>Provides:
 * <ul>
 *   <li>Foul probability modulated by style, stamina, position, defending state</li>
 *   <li>Yellow card probability after a foul, modulated by existing cards and style</li>
 *   <li>Second-yellow detection for RED_CARD event generation</li>
 * </ul>
 *
 * <p>No mutable state, no side effects. Deterministic via provided Random.
 * No Spring annotations, no repository dependencies.
 */
public final class V24DisciplineModel {

    // Foul probability bounds
    private static final double MIN_FOUL_PROB = 0.005;
    private static final double MAX_FOUL_PROB = 0.12;

    // Yellow card probability bounds (after a foul)
    private static final double MIN_YELLOW_PROB = 0.10;
    private static final double MAX_YELLOW_PROB = 0.80;

    /**
     * Foul probability for a player given style and defending state.
     * Modulated by TeamStyle, stamina level, position, and defending flag.
     */
    public double foulProbability(V24PlayerMatchState player, TeamStyle style, boolean defending) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        double base = 0.04;

        // Style modifier
        if (style == null) style = TeamStyle.BALANCED;
        double styleMod = switch (style) {
            case DEFENSIVE -> 0.020;
            case COUNTER -> 0.010;
            case ATTACKING -> 0.005;
            case POSSESSION -> -0.010;
            case BALANCED -> 0.000;
        };

        // Stamina modifier
        int stamina = player.currentStamina();
        double staminaMod = 0.0;
        if (stamina < 30) {
            staminaMod = 0.030;
        } else if (stamina < 50) {
            staminaMod = 0.015;
        }

        // Position modifier (defender more likely to foul)
        double posMod = isDefenderPosition(player.position()) ? 0.010 : 0.0;

        // Defending state modifier
        double defMod = defending ? 0.010 : 0.0;

        double prob = base + styleMod + staminaMod + posMod + defMod;
        return clamp(prob, MIN_FOUL_PROB, MAX_FOUL_PROB);
    }

    /**
     * Yellow card probability after a foul has been committed.
     * Modulated by existing yellow cards, style, and stamina.
     */
    public double yellowCardProbability(V24PlayerMatchState player, TeamStyle style) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        double base = 0.40;

        // Existing yellows: player already has 1 yellow
        int existingYellows = player.yellowCards();
        double existingMod = (existingYellows >= 1) ? 0.15 : 0.0;

        // Style modifier
        if (style == null) style = TeamStyle.BALANCED;
        double styleMod = switch (style) {
            case DEFENSIVE -> 0.050;
            case BALANCED -> 0.020;
            case POSSESSION -> -0.030;
            default -> 0.0; // ATTACKING, COUNTER
        };

        // Low stamina increases card risk
        int stamina = player.currentStamina();
        double staminaMod = (stamina < 30) ? 0.050 : 0.0;

        double prob = base + existingMod + styleMod + staminaMod;
        return clamp(prob, MIN_YELLOW_PROB, MAX_YELLOW_PROB);
    }

    /**
     * Decide if a player should commit a foul, using foul probability and random roll.
     */
    public boolean shouldCommitFoul(V24PlayerMatchState player, TeamStyle style,
                                    boolean defending, Random random) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        double prob = foulProbability(player, style, defending);
        return random.nextDouble() < prob;
    }

    /**
     * Decide if a player should receive a yellow card after committing a foul.
     */
    public boolean shouldReceiveYellow(V24PlayerMatchState player, TeamStyle style, Random random) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        double prob = yellowCardProbability(player, style);
        return random.nextDouble() < prob;
    }

    /**
     * Returns true if the player should produce a RED_CARD event.
     * True when the player has received at least 2 yellow cards.
     */
    public boolean shouldProduceRedCard(V24PlayerMatchState player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        return player.yellowCards() >= 2;
    }

    /**
     * Check if player was just sent off (red card == true).
     */
    public boolean isRedCarded(V24PlayerMatchState player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        return player.redCard();
    }

    private boolean isDefenderPosition(String position) {
        return "DEF".equals(position);
    }

    private double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}