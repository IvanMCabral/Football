package com.footballmanager.application.service.simulation.v24;

/**
 * Shot zone classification for xG calculation.
 * Zones are ordered by decreasing proximity to goal.
 */
public enum V24ShotLocation {
    SIX_YARD_BOX,        // xG ≈ 0.40–0.60
    PENALTY_AREA_CENTER, // xG ≈ 0.20–0.35
    PENALTY_AREA_WIDE,   // xG ≈ 0.12–0.20
    OUTSIDE_BOX,         // xG ≈ 0.05–0.10
    LONG_RANGE           // xG ≈ 0.01–0.05
}