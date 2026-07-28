package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;

final class V24MatchProbabilityService {

    private static final double HOME_CHANCE_VOLUME_ADVANTAGE = 1.040;
    private static final double AWAY_CHANCE_VOLUME_FRICTION = 0.985;

    double professionalShotTempoMultiplier() {
        return 1.00;
    }

    double homeFieldChanceVolumeMultiplier(boolean homeHasPossession) {
        return homeHasPossession ? HOME_CHANCE_VOLUME_ADVANTAGE : AWAY_CHANCE_VOLUME_FRICTION;
    }

    double collectiveQualityChanceVolumeMultiplier(double possessorCollectiveStat, double opponentCollectiveStat) {
        double edge = possessorCollectiveStat - opponentCollectiveStat;
        return clamp(1.0 + (edge * 0.022), 0.89, 1.11);
    }

    double defensiveShapeShotQualityMultiplier(V24TacticalShapeProfile defense, V24ShotLocation location) {
        if (defense == null || location == null) {
            return 1.0;
        }

        double centralCover = defense.defenseCenter();
        double wideCover = (defense.defenseLeft() + defense.defenseRight()) / 2.0;
        double laneCover = switch (location) {
            case SIX_YARD_BOX, PENALTY_AREA_CENTER -> centralCover;
            case PENALTY_AREA_WIDE -> wideCover;
            case OUTSIDE_BOX -> (centralCover * 0.65) + (wideCover * 0.35);
            case LONG_RANGE -> centralCover;
        };

        double laneEffect = (laneCover - 1.0) * switch (location) {
            case SIX_YARD_BOX -> 0.155;
            case PENALTY_AREA_CENTER -> 0.135;
            case PENALTY_AREA_WIDE -> 0.125;
            case OUTSIDE_BOX -> 0.070;
            case LONG_RANGE -> 0.045;
        };
        double resistanceEffect = (1.0 - defense.defensiveResistanceMultiplier()) * 0.220;
        return clamp(1.0 - laneEffect - resistanceEffect, 0.72, 1.18);
    }

    double styleToModifier(TeamStyle style) {
        return switch (style) {
            case ATTACKING -> 1.15;
            case POSSESSION -> 1.05;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 1.04;
            case CENTRAL_PLAY -> 1.02;
            case BALANCED -> 1.00;
            case COUNTER -> 0.95;
            case DEFENSIVE -> 0.85;
        };
    }

    double defensiveStyleChanceVolumeMultiplier(TeamStyle defendingStyle) {
        if (defendingStyle == null) {
            return 1.0;
        }
        return switch (defendingStyle) {
            case DEFENSIVE -> 0.82;
            case COUNTER -> 0.91;
            case POSSESSION -> 0.96;
            case BALANCED -> 1.00;
            case CENTRAL_PLAY -> 1.01;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 1.01;
            case ATTACKING -> 1.08;
        };
    }

    double defensiveStyleShotQualityMultiplier(TeamStyle defendingStyle, V24ShotLocation location) {
        if (defendingStyle == null || location == null) {
            return 1.0;
        }
        double base = switch (defendingStyle) {
            case DEFENSIVE -> 0.91;
            case COUNTER -> 0.96;
            case POSSESSION -> 0.98;
            case BALANCED -> 1.00;
            case CENTRAL_PLAY -> 1.01;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 1.01;
            case ATTACKING -> 1.06;
        };
        double laneAdjustment = switch (location) {
            case SIX_YARD_BOX, PENALTY_AREA_CENTER -> defendingStyle == TeamStyle.DEFENSIVE ? 0.97 : 1.0;
            case PENALTY_AREA_WIDE -> defendingStyle == TeamStyle.WIDE_PLAY
                || defendingStyle == TeamStyle.LEFT_FLANK
                || defendingStyle == TeamStyle.RIGHT_FLANK ? 0.98 : 1.0;
            case OUTSIDE_BOX -> 1.0;
            case LONG_RANGE -> defendingStyle == TeamStyle.DEFENSIVE ? 0.95 : 1.0;
        };
        return clamp(base * laneAdjustment, 0.84, 1.10);
    }

    double chanceProbability(
            TeamStyle style,
            int minute,
            int possessorAttack,
            int possessorSpeed,
            int dribblerSkill,
            int speedsterSkill) {
        double base = switch (style) {
            case ATTACKING -> 0.42;
            case POSSESSION -> 0.38;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> 0.36;
            case CENTRAL_PLAY -> 0.34;
            case COUNTER -> 0.35;
            case DEFENSIVE -> 0.28;
            case BALANCED -> 0.35;
        };
        double secondHalf = (minute > 45) ? 1.15 : 1.0;
        double endGame = (minute > 75) ? 1.2 : 1.0;
        int effectiveSpeed = possessorSpeed;
        if (style == TeamStyle.COUNTER && speedsterSkill > 0) {
            effectiveSpeed += speedsterSkill / 3;
        }
        double qualityMod = 1.0
            + (possessorAttack - 70) * 0.02
            + (effectiveSpeed - 70) * 0.01;
        double dribblerMult = 1.0 + (dribblerSkill / 600.0);

        return base * secondHalf * endGame * qualityMod * dribblerMult;
    }

    double possessionBase(TeamStyle style) {
        return switch (style) {
            case POSSESSION -> 58.0;
            case ATTACKING -> 52.0;
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK, CENTRAL_PLAY -> 50.0;
            case COUNTER -> 48.0;
            case DEFENSIVE -> 45.0;
            case BALANCED -> 50.0;
        };
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
