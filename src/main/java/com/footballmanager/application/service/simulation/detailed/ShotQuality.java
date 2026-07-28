package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.Objects;

/**
 * Immutable input bundle for shot xG computation.
 * V24A: this is a pure input record — no xG formula applied here.
 * Clamps numeric fields to safe ranges.
 */
public final class ShotQuality {

    private final ShotLocation location;
    private final double shooterQuality;
    private final double assistQuality;
    private final double defensivePressure;
    private final double goalkeeperQuality;
    private final double tacticModifier;

    public ShotQuality(
            ShotLocation location,
            double shooterQuality,
            double assistQuality,
            double defensivePressure,
            double goalkeeperQuality,
            double tacticModifier) {
        this.location = Objects.requireNonNull(location, "location must not be null");
        this.shooterQuality = clampFinite(shooterQuality, 0.0, 100.0, "shooterQuality");
        this.assistQuality = clampFinite(assistQuality, 0.0, 100.0, "assistQuality");
        this.defensivePressure = clampFinite(defensivePressure, 0.0, 100.0, "defensivePressure");
        this.goalkeeperQuality = clampFinite(goalkeeperQuality, 0.0, 100.0, "goalkeeperQuality");
        this.tacticModifier = clampFinite(tacticModifier, 0.5, 1.5, "tacticModifier");
    }

    private static double clampFinite(double value, double min, double max, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        return Math.max(min, Math.min(max, value));
    }

    public ShotLocation location() { return location; }
    public double shooterQuality() { return shooterQuality; }
    public double assistQuality() { return assistQuality; }
    public double defensivePressure() { return defensivePressure; }
    public double goalkeeperQuality() { return goalkeeperQuality; }
    public double tacticModifier() { return tacticModifier; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShotQuality that)) return false;
        return location == that.location
                && Double.compare(that.shooterQuality, shooterQuality) == 0
                && Double.compare(that.assistQuality, assistQuality) == 0
                && Double.compare(that.defensivePressure, defensivePressure) == 0
                && Double.compare(that.goalkeeperQuality, goalkeeperQuality) == 0
                && Double.compare(that.tacticModifier, tacticModifier) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, shooterQuality, assistQuality, defensivePressure, goalkeeperQuality, tacticModifier);
    }

    @Override
    public String toString() {
        return "ShotQuality{location=%s, shooter=%.1f, assist=%.1f, pressure=%.1f, gk=%.1f, tactic=%.2f}"
                .formatted(location, shooterQuality, assistQuality, defensivePressure, goalkeeperQuality, tacticModifier);
    }
}
