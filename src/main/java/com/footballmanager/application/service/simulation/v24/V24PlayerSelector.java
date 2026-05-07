package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * V24B: Selects shooter and assist provider from team match state.
 * Uses position-based weights and attribute-based quality scores.
 */
public class V24PlayerSelector {

    private final Random random;

    public V24PlayerSelector(Random random) {
        this.random = random;
    }

    /**
     * Select the best shooter from the given list, using position weights and attack attribute.
     * Forwards (ATT/WINGER) and midfielders are preferred over defenders.
     * Only selects from on-pitch, non-injured, non-red-carded players.
     */
    public Optional<V24PlayerMatchState> selectShooter(List<V24PlayerMatchState> players) {
        var candidates = players.stream()
                .filter(p -> p.onPitch() && !p.injured() && !p.redCard())
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Weight by attack attribute (higher = more likely to shoot)
        double totalWeight = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            double w = attackWeight(candidates.get(i));
            weights[i] = w;
            totalWeight += w;
        }

        double roll = random.nextDouble() * totalWeight;
        double cum = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cum += weights[i];
            if (roll <= cum) {
                return Optional.of(candidates.get(i));
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }

    /**
     * Select an assist provider from the given list, excluding the shooter.
     * Prefers midfielders and wingers for creative passing.
     */
    public Optional<V24PlayerMatchState> selectAssistProvider(
            List<V24PlayerMatchState> players,
            V24PlayerMatchState shooter) {

        var candidates = players.stream()
                .filter(p -> p.onPitch() && !p.injured() && !p.redCard() && !p.equals(shooter))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        double totalWeight = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            double w = assistWeight(candidates.get(i));
            weights[i] = w;
            totalWeight += w;
        }

        double roll = random.nextDouble() * totalWeight;
        double cum = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cum += weights[i];
            if (roll <= cum) {
                return Optional.of(candidates.get(i));
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }

    /**
     * Normalized shooter quality [0,1] from attack attribute and form.
     */
    public double shooterQuality(V24PlayerMatchState player) {
        int attack = player.attack();
        int form = player.form();
        // attack: 0-99 normalized; form: 0-100 normalized, weighted 60/40
        double aNorm = attack / 99.0;
        double fNorm = form / 100.0;
        return Math.round((aNorm * 0.6 + fNorm * 0.4) * 1000.0) / 1000.0;
    }

    /**
     * Normalized assist quality [0,1] from technique attribute.
     */
    public double assistQuality(V24PlayerMatchState player) {
        int technique = player.technique();
        return Math.round((technique / 99.0) * 1000.0) / 1000.0;
    }

    private double attackWeight(V24PlayerMatchState p) {
        double base = p.attack() / 50.0; // normalize to ~1.0 at attack=50
        double posBonus = positionShootingBonus(p.position());
        return base * posBonus;
    }

    private double assistWeight(V24PlayerMatchState p) {
        double tech = p.technique() / 50.0;
        double posBonus = positionPassingBonus(p.position());
        return tech * posBonus;
    }

    private double positionShootingBonus(String pos) {
        return switch (pos) {
            case "ATT" -> 2.0;
            case "WINGER" -> 1.7;
            case "MID" -> 1.2;
            case "DEF" -> 0.6;
            case "GK" -> 0.1;
            default -> 1.0;
        };
    }

    private double positionPassingBonus(String pos) {
        return switch (pos) {
            case "MID" -> 2.0;
            case "WINGER" -> 1.8;
            case "ATT" -> 1.4;
            case "DEF" -> 0.8;
            case "GK" -> 0.1;
            default -> 1.0;
        };
    }
}