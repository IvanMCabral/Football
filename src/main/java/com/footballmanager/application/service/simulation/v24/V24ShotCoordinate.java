package com.footballmanager.application.service.simulation.v24;

import java.util.Objects;

/**
 * Immutable shot coordinate with pitch position and derived goal metrics.
 * V24D3A: shot coordinate value object — descriptive metadata only.
 *
 * <p>Coordinate system:
 * <ul>
 *   <li>x: 0 to 100, attacking direction toward 100</li>
 *   <li>y: 0 to 100, left to right</li>
 *   <li>Goal center: x=100, y=50</li>
 * </ul>
 *
 * <p>Derived values computed on construction — no external state, no randomness.
 */
public final class V24ShotCoordinate {

    private final double x;
    private final double y;
    private final V24ShotLocation location;
    private final double distanceToGoal;
    private final double angleToGoal;
    private final boolean insideBox;

    public V24ShotCoordinate(double x, double y, V24ShotLocation location) {
        this.x = validateX(x);
        this.y = validateY(y);
        this.location = Objects.requireNonNull(location, "location must not be null");

        // Derived values
        this.distanceToGoal = computeDistance(this.x);
        this.angleToGoal = computeAngle(this.x, this.y);
        this.insideBox = computeInsideBox(this.x, this.y);
    }

    private static double validateX(double x) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("x must be finite, got " + x);
        }
        if (x < 0.0 || x > 100.0) {
            throw new IllegalArgumentException("x must be between 0 and 100, got " + x);
        }
        return x;
    }

    private static double validateY(double y) {
        if (!Double.isFinite(y)) {
            throw new IllegalArgumentException("y must be finite, got " + y);
        }
        if (y < 0.0 || y > 100.0) {
            throw new IllegalArgumentException("y must be between 0 and 100, got " + y);
        }
        return y;
    }

    /**
     * Euclidean distance from shot point to goal center (100, 50).
     * Range: approximately 5–65 for valid shot locations.
     */
    private static double computeDistance(double shotX) {
        double dx = 100.0 - shotX;
        double dy = 50.0 - 50.0; // y always 50 for goal center
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Angle from shot point to goal center, in degrees.
     * Returns approximately [-90, 90] where 0 = straight on goal.
     */
    private static double computeAngle(double shotX, double shotY) {
        double dx = 100.0 - shotX;
        double dy = 50.0 - shotY;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        // Result of atan2 is already in (-180, 180] — clamp for sanity
        return Math.max(-90.0, Math.min(90.0, angle));
    }

    /**
     * True when shot is inside the penalty area.
     * Approximation: x >= 83 AND 21 <= y <= 79.
     */
    private static boolean computeInsideBox(double shotX, double shotY) {
        return shotX >= 83.0 && shotY >= 21.0 && shotY <= 79.0;
    }

    public double x() { return x; }
    public double y() { return y; }
    public V24ShotLocation location() { return location; }
    public double distanceToGoal() { return distanceToGoal; }
    public double angleToGoal() { return angleToGoal; }
    public boolean insideBox() { return insideBox; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof V24ShotCoordinate that)) return false;
        return Double.compare(that.x, x) == 0
                && Double.compare(that.y, y) == 0
                && location == that.location
                && Double.compare(that.distanceToGoal, distanceToGoal) == 0
                && Double.compare(that.angleToGoal, angleToGoal) == 0
                && insideBox == that.insideBox;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, location, distanceToGoal, angleToGoal, insideBox);
    }

    @Override
    public String toString() {
        return "V24ShotCoordinate{x=%.2f, y=%.2f, location=%s, dist=%.2f, angle=%.2f, insideBox=%s}"
                .formatted(x, y, location, distanceToGoal, angleToGoal, insideBox);
    }
}
