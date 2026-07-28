package com.footballmanager.application.service.simulation.detailed;

record TacticalShapeProfile(
        double possessionMultiplier,
        double attackVolumeMultiplier,
        double defensiveResistanceMultiplier,
        double attackLeft,
        double attackCenter,
        double attackRight,
        double defenseLeft,
        double defenseCenter,
        double defenseRight
) {
}
