package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * V24B: Selects shooter and assist provider from team match state.
 * Uses position-based weights and attribute-based quality scores.
 */
public class PlayerSelector {

    private final Random random;
    private final FormationParser formationParser;

    public PlayerSelector(Random random) {
        this.random = random;
        this.formationParser = new FormationParser();
    }

    /**
     * Select the best shooter from the given list, using position weights and attack attribute.
     * Forwards (ATT/WINGER) and midfielders are preferred over defenders.
     * Only selects from on-pitch, non-injured, non-red-carded players.
     */
    public Optional<PlayerMatchState> selectShooter(List<PlayerMatchState> players) {
        if (players == null) {
            throw new IllegalArgumentException("players list cannot be null");
        }
        return selectShooter(players, (String) null);
    }

    /**
     * Select the best shooter with optional formation-aware weighting.
     * When formation is null/blank, falls back to position-based weights.
     */
    public Optional<PlayerMatchState> selectShooter(List<PlayerMatchState> players, String formation) {
        if (players == null) {
            throw new IllegalArgumentException("players list cannot be null");
        }
        var candidates = players.stream()
                .filter(p -> p.onPitch() && !p.injured() && !p.redCard())
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        FormationParser.FormationShape f = formationParser.parse(formation);
        return selectWeightedShooter(candidates, f);
    }

    /**
     * Select the best shooter with explicit FormationShape.
     */
    public Optional<PlayerMatchState> selectShooter(List<PlayerMatchState> players,
                                                      FormationParser.FormationShape formation) {
        var candidates = players.stream()
                .filter(p -> p.onPitch() && !p.injured() && !p.redCard())
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return selectWeightedShooter(candidates, formation);
    }

    private Optional<PlayerMatchState> selectWeightedShooter(List<PlayerMatchState> candidates,
                                                                FormationParser.FormationShape formation) {
        double totalWeight = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            double w = formationAttackWeight(candidates.get(i), formation);
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
    public Optional<PlayerMatchState> selectAssistProvider(
            List<PlayerMatchState> players,
            PlayerMatchState shooter) {

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
    public double shooterQuality(PlayerMatchState player) {
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
    public double assistQuality(PlayerMatchState player) {
        int technique = player.technique();
        return Math.round((technique / 99.0) * 1000.0) / 1000.0;
    }

    private double formationAttackWeight(PlayerMatchState p, FormationParser.FormationShape formation) {
        double base = p.attack() / 50.0;
        double posBonus = formationShootingBonus(p.position(), formation);
        return base * posBonus;
    }

    private double assistWeight(PlayerMatchState p) {
        double tech = p.technique() / 50.0;
        double posBonus = positionPassingBonus(p.position());
        return tech * posBonus;
    }

    /**
     * Formation-aware shooting bonus.
     * ATT gets highest priority in formations with dedicated forwards.
     * WINGER gets high priority in formations with wingers.
     * MID gets moderate priority.
     * DEF gets low priority unless back-three with no attacking options.
     * GK always gets near-zero.
     */
    private double formationShootingBonus(String pos, FormationParser.FormationShape formation) {
        boolean hasForwards = formation.forwards() > 0;
        boolean hasWingers = formation.hasWingers();
        boolean isBackThree = formation.isBackThree();
        boolean isBackFive = formation.isBackFive();
        boolean is4231 = "4-2-3-1".equals(formation.raw());
        boolean is352 = "3-5-2".equals(formation.raw());
        boolean isBackFour = formation.isBackFour();

        return switch (pos) {
            case "ATT" -> {
                if (isBackFive && !hasForwards) yield 0.8;
                yield 2.2; // strikers get highest priority
            }
            case "WINGER" -> {
                if (hasWingers) yield 1.8;
                if (isBackFive) yield 1.4;
                yield 1.5;
            }
            case "MID" -> {
                if (isBackFive) yield 1.3;
                if (is4231) yield 1.5; // AM-level priority in 4-2-3-1
                if (is352) yield 1.4;
                yield 1.2;
            }
            case "DEF" -> {
                if (isBackThree && !hasForwards && !hasWingers) yield 1.0;
                if (isBackFive && formation.midfielders() <= 3) yield 0.7;
                if (isBackFour) yield 0.6;
                yield 0.5;
            }
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
