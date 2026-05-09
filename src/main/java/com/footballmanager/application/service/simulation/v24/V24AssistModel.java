package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * V24D2: Assist and key-pass model for V24 detailed match engine.
 *
 * <p>Computes assist probability from shooter, candidate, formation, and style.
 * Provides deterministic assist credit decision based on weighted probability.
 * No mutable state, no Spring, no repositories.
 */
public final class V24AssistModel {

    private final V24FormationParser formationParser = new V24FormationParser();

    /**
     * Select an assist provider for a given shooter using formation-aware weighting.
     * Excludes the shooter, off-pitch players, injured players, and red-carded players.
     * Returns Optional.empty if no eligible provider exists.
     */
    public Optional<V24PlayerMatchState> selectAssistProvider(
            List<V24PlayerMatchState> candidates,
            V24PlayerMatchState shooter,
            String formation,
            TeamStyle style,
            Random random) {

        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(shooter, "shooter must not be null");
        Objects.requireNonNull(random, "random must not be null");

        var eligible = candidates.stream()
                .filter(p -> p.onPitch() && !p.injured() && !p.redCard() && !p.equals(shooter))
                .toList();

        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        // Compute weights for each eligible candidate
        double totalWeight = 0.0;
        double[] weights = new double[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) {
            double w = assistWeight(eligible.get(i), formation, style);
            weights[i] = w;
            totalWeight += w;
        }

        if (totalWeight <= 0.0) {
            return Optional.empty();
        }

        double roll = random.nextDouble() * totalWeight;
        double cum = 0.0;
        for (int i = 0; i < eligible.size(); i++) {
            cum += weights[i];
            if (roll <= cum) {
                return Optional.of(eligible.get(i));
            }
        }
        return Optional.of(eligible.get(eligible.size() - 1));
    }

    /**
     * Compute assist probability for a candidate being assist provider.
     * Clamped to [0.10, 0.85].
     */
    public double assistProbability(
            V24PlayerMatchState shooter,
            V24PlayerMatchState candidate,
            String formation,
            TeamStyle style) {

        Objects.requireNonNull(shooter, "shooter must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");

        double base = 0.55; // base assist probability for GOAL events

        // Style modifier
        double styleMod = styleModifier(style);

        // Position modifier
        double posMod = positionModifier(candidate.position());

        // Formation modifier
        double formMod = formationModifier(formation, candidate.position());

        // Stamina penalty
        double staminaMod = candidate.currentStamina() < 30 ? -0.05 : 0.0;

        double raw = base + styleMod + posMod + formMod + staminaMod;
        return clamp(raw, 0.10, 0.85);
    }

    /**
     * Decide whether to credit an assist for a given shot/goal.
     * Uses assistProbability + random roll to make deterministic decision.
     */
    public boolean shouldCreditAssist(
            V24PlayerMatchState shooter,
            V24PlayerMatchState candidate,
            String formation,
            TeamStyle style,
            Random random) {

        Objects.requireNonNull(shooter, "shooter must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(random, "random must not be null");

        double prob = assistProbability(shooter, candidate, formation, style);
        return random.nextDouble() < prob;
    }

    private double assistWeight(V24PlayerMatchState p, String formation, TeamStyle style) {
        double base = p.technique() / 50.0;
        double posBonus = positionAssistBonus(p.position(), formation);
        double styleBonus = styleModifier(style) * 0.5;
        return base * posBonus * (1.0 + styleBonus);
    }

    private double positionAssistBonus(String pos, String formation) {
        V24FormationParser.V24Formation f = formationParser.parse(formation);
        boolean is4231 = "4-2-3-1".equals(f.raw());
        boolean is433 = "4-3-3".equals(f.raw());
        boolean is352 = "3-5-2".equals(f.raw());

        return switch (pos) {
            case "MID" -> {
                if (is4231 || is352) yield 2.2;
                yield 2.0;
            }
            case "WINGER" -> {
                if (is433 || is4231) yield 2.0;
                yield 1.8;
            }
            case "ATT" -> 1.4;
            case "DEF" -> 0.8;
            case "GK" -> 0.1; // GK effectively excluded
            default -> 1.0;
        };
    }

    private double positionModifier(String pos) {
        return switch (pos) {
            case "MID" -> 0.08;
            case "WINGER" -> 0.07;
            case "ATT" -> 0.03;
            case "DEF" -> -0.03;
            case "GK" -> -0.10; // GK never preferred as assist provider
            default -> 0.0;
        };
    }

    private double styleModifier(TeamStyle style) {
        if (style == null) style = TeamStyle.BALANCED;
        return switch (style) {
            case POSSESSION -> 0.08;
            case ATTACKING -> 0.05;
            case COUNTER -> 0.03;
            case DEFENSIVE -> -0.05;
            case BALANCED -> 0.0;
        };
    }

    private double formationModifier(String formation, String pos) {
        V24FormationParser.V24Formation f = formationParser.parse(formation);
        String raw = f.raw();

        if ("MID".equals(pos)) {
            if ("4-2-3-1".equals(raw)) return 0.05;
            if ("3-5-2".equals(raw)) return 0.04;
        }
        if ("WINGER".equals(pos)) {
            if ("4-3-3".equals(raw)) return 0.05;
            if ("4-2-3-1".equals(raw)) return 0.05;
        }
        if ("5-4-1".equals(raw)) return -0.03;

        return 0.0;
    }

    private double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}