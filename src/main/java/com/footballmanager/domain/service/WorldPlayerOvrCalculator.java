package com.footballmanager.domain.service;

/**
 * Canonical base OVR calculation for {@code WorldPlayer}.
 *
 * This class intentionally preserves the existing WorldPlayer contract,
 * including its exact string-position switch and default arithmetic mean.
 * Player/SessionPlayer's separate OverallCalculator is not this authority.
 */
public final class WorldPlayerOvrCalculator {

    private WorldPlayerOvrCalculator() {
    }

    public static int calculate(Integer attack,
                                Integer defense,
                                Integer technique,
                                Integer speed,
                                Integer stamina,
                                Integer mentality,
                                String position) {
        if (attack == null || defense == null || technique == null
                || speed == null || stamina == null || mentality == null) {
            return 50;
        }

        double overall = switch (position) {
            case "GK" ->
                    defense * 0.40
                            + technique * 0.20
                            + mentality * 0.20
                            + stamina * 0.10
                            + speed * 0.05
                            + attack * 0.05;
            case "DEF" ->
                    defense * 0.35
                            + technique * 0.15
                            + mentality * 0.15
                            + stamina * 0.15
                            + speed * 0.10
                            + attack * 0.10;
            case "MID" ->
                    technique * 0.30
                            + stamina * 0.20
                            + mentality * 0.15
                            + defense * 0.15
                            + speed * 0.10
                            + attack * 0.10;
            case "WINGER" ->
                    speed * 0.30
                            + attack * 0.25
                            + technique * 0.20
                            + stamina * 0.15
                            + mentality * 0.05
                            + defense * 0.05;
            case "ATT" ->
                    attack * 0.40
                            + technique * 0.20
                            + speed * 0.15
                            + mentality * 0.10
                            + stamina * 0.10
                            + defense * 0.05;
            default ->
                    (attack + defense + technique + speed + stamina + mentality) / 6.0;
        };

        return (int) Math.round(overall);
    }
}
