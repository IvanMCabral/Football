package com.footballmanager.application.service.simulation.detailed;

import java.util.Objects;

record MinuteSimulationConfig(
        MatchContext matchContext,
        double homePossessionBase,
        double awayPossessionBase,
        double matchIntensity) {

    MinuteSimulationConfig {
        Objects.requireNonNull(matchContext, "matchContext must not be null");
        requireFinite(homePossessionBase, "homePossessionBase");
        requireFinite(awayPossessionBase, "awayPossessionBase");
        requireFinite(matchIntensity, "matchIntensity");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
