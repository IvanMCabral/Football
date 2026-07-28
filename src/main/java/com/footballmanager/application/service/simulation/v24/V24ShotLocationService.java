package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.Random;

final class V24ShotLocationService {

    private static final V24ShotLocation[] LOCATIONS = V24ShotLocation.values();
    private static final V24FormationParser FORMATION_PARSER = new V24FormationParser();

    private final V24ShotCoordinateGenerator coordGenerator = new V24ShotCoordinateGenerator();

    V24ShotLocation selectShotLocation(
            TeamStyle style,
            String formation,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape,
            Random random) {
        double[] weights = computeLocationWeights(style, formation, possessorShape, opponentShape);
        double total = weights[0] + weights[1] + weights[2] + weights[3] + weights[4];
        if (total <= 0.0) {
            return V24ShotLocation.PENALTY_AREA_CENTER;
        }
        double roll = random.nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < LOCATIONS.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return LOCATIONS[i];
            }
        }
        return LOCATIONS[LOCATIONS.length - 1];
    }

    V24ShotCoordinate generateShotCoordinate(
            V24ShotLocation location,
            TeamStyle style,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape,
            Random random) {
        if (location != V24ShotLocation.PENALTY_AREA_WIDE
            || style == TeamStyle.LEFT_FLANK
            || style == TeamStyle.RIGHT_FLANK
            || possessorShape == null
            || opponentShape == null) {
            return generateShotCoordinate(location, style, random);
        }
        double leftOpportunity = flankExploitOpportunity(possessorShape.attackLeft(), opponentShape.defenseRight());
        double rightOpportunity = flankExploitOpportunity(possessorShape.attackRight(), opponentShape.defenseLeft());
        double opportunityGap = Math.abs(leftOpportunity - rightOpportunity);
        if (opportunityGap < 0.04) {
            return generateShotCoordinate(location, style, random);
        }

        boolean attackLeft = leftOpportunity > rightOpportunity;
        double bias = clamp(0.50 + opportunityGap, 0.50, 0.88);
        if (random.nextDouble() < bias) {
            return coordGenerator.generateWideFlank(attackLeft, random);
        }
        return coordGenerator.generateWideFlank(!attackLeft, random);
    }

    private V24ShotCoordinate generateShotCoordinate(V24ShotLocation location, TeamStyle style, Random random) {
        if (location == V24ShotLocation.PENALTY_AREA_WIDE && style == TeamStyle.LEFT_FLANK) {
            return coordGenerator.generateWideFlank(true, random);
        }
        if (location == V24ShotLocation.PENALTY_AREA_WIDE && style == TeamStyle.RIGHT_FLANK) {
            return coordGenerator.generateWideFlank(false, random);
        }
        return coordGenerator.generate(location, random);
    }

    private double[] computeLocationWeights(
            TeamStyle style,
            String formation,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape) {
        double[] weights = { 0.25, 0.27, 0.20, 0.18, 0.10 };
        double[] shift = styleLocationShift(style);
        for (int i = 0; i < weights.length; i++) {
            weights[i] *= shift[i];
        }
        applyFormationShapeShift(weights, formation);
        applyNamedFormationIdentityLocationShift(weights, formation);
        applyTacticalShapeShift(weights, possessorShape, opponentShape);
        return weights;
    }

    private void applyFormationShapeShift(double[] weights, String formation) {
        V24FormationParser.V24Formation parsed = FORMATION_PARSER.parse(formation);
        if (parsed.hasWingers()) {
            weights[2] *= 1.25;
            weights[0] *= 0.90;
        }
        if (parsed.defenders() == 3) {
            if (parsed.hasWingers()) {
                weights[2] *= 0.88;
                weights[1] *= 1.05;
            } else {
                weights[2] *= 0.62;
                weights[1] *= 1.12;
            }
        }
        if (parsed.forwards() == 1) {
            weights[0] *= 1.30;
            weights[4] *= 0.70;
        }
        if (parsed.forwards() == 2) {
            weights[1] *= 1.20;
        }
    }

    private void applyNamedFormationIdentityLocationShift(double[] weights, String formation) {
        if (weights == null || weights.length < 5 || formation == null) {
            return;
        }
        switch (formation) {
            case "4-3-3" -> {
                weights[0] *= 0.93;
                weights[2] *= 1.32;
                weights[4] *= 0.90;
            }
            case "4-2-2-2" -> {
                weights[1] *= 1.10;
                weights[2] *= 0.88;
                weights[3] *= 1.06;
            }
            case "4-1-2-3" -> {
                weights[0] *= 0.94;
                weights[1] *= 1.08;
                weights[4] *= 0.92;
            }
            case "3-5-2-CDM" -> {
                weights[1] *= 1.07;
                weights[2] *= 0.92;
                weights[4] *= 0.95;
            }
            default -> {
            }
        }
    }

    private void applyTacticalShapeShift(
            double[] weights,
            V24TacticalShapeProfile possessorShape,
            V24TacticalShapeProfile opponentShape) {
        if (possessorShape == null || opponentShape == null) {
            return;
        }
        double centralAttack = possessorShape.attackCenter();
        double wideAttack = (possessorShape.attackLeft() + possessorShape.attackRight()) / 2.0;
        double centralDefense = opponentShape.defenseCenter();
        double wideDefense = (opponentShape.defenseLeft() + opponentShape.defenseRight()) / 2.0;

        double centralEdge = centralAttack - centralDefense;
        double wideEdge = wideAttack - wideDefense;
        double flankImbalance = Math.abs(possessorShape.attackLeft() - possessorShape.attackRight());
        double leftFlankEdge = flankExploitOpportunity(possessorShape.attackLeft(), opponentShape.defenseRight());
        double rightFlankEdge = flankExploitOpportunity(possessorShape.attackRight(), opponentShape.defenseLeft());
        double bestFlankEdge = Math.max(leftFlankEdge, rightFlankEdge);
        double flankExploitGap = Math.abs(leftFlankEdge - rightFlankEdge);

        weights[0] *= clamp(1.0 + centralEdge * 0.18, 0.86, 1.18);
        weights[1] *= clamp(1.0 + centralEdge * 0.22, 0.84, 1.22);
        weights[2] *= clamp(1.0 + wideEdge * 0.22, 0.80, 1.22);
        weights[2] *= clamp(1.0 + Math.max(0.0, bestFlankEdge) * 0.16 + flankExploitGap * 0.18, 0.92, 1.18);
        weights[3] *= clamp(1.0 + Math.max(0.0, wideDefense - wideAttack) * 0.14, 0.92, 1.16);
        weights[4] *= clamp(1.0 + Math.max(0.0, centralDefense - centralAttack) * 0.12, 0.94, 1.14);
        weights[1] *= clamp(1.0 - flankImbalance * 0.10, 0.88, 1.0);
    }

    private double flankExploitOpportunity(double attackLane, double mirroredOpponentDefenseLane) {
        double vulnerability = Math.max(0.0, 1.0 - mirroredOpponentDefenseLane);
        return (attackLane * 0.90) - (mirroredOpponentDefenseLane * 0.70) + (vulnerability * 0.35);
    }

    private double[] styleLocationShift(TeamStyle style) {
        return switch (style) {
            case ATTACKING -> new double[] { 1.60, 1.11, 0.85, 0.50, 0.30 };
            case POSSESSION -> new double[] { 1.20, 1.11, 0.90, 0.56, 0.30 };
            case WIDE_PLAY, LEFT_FLANK, RIGHT_FLANK -> new double[] { 0.98, 0.94, 1.42, 0.98, 0.86 };
            case CENTRAL_PLAY -> new double[] { 1.12, 1.20, 0.72, 0.94, 0.86 };
            case COUNTER -> new double[] { 0.72, 1.11, 0.90, 0.94, 0.60 };
            case DEFENSIVE -> new double[] { 0.40, 0.93, 0.90, 1.22, 0.90 };
            default -> new double[] { 1.00, 1.00, 1.00, 1.00, 1.00 };
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
