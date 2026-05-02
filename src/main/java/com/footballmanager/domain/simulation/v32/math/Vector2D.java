package com.footballmanager.domain.simulation.v32.math;

/**
 * Immutable 2D vector for V32 simulation.
 */
public final class Vector2D {

    public final double x;
    public final double y;

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static Vector2D zero() {
        return new Vector2D(0, 0);
    }

    public static Vector2D fromPolar(double angle, double magnitude) {
        return new Vector2D(Math.cos(angle) * magnitude, Math.sin(angle) * magnitude);
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y);
    }

    public double magnitudeSquared() {
        return x * x + y * y;
    }

    public Vector2D normalize() {
        double mag = magnitude();
        if (mag < 0.0001) return zero();
        return new Vector2D(x / mag, y / mag);
    }

    public Vector2D add(Vector2D other) {
        return new Vector2D(x + other.x, y + other.y);
    }

    public Vector2D subtract(Vector2D other) {
        return new Vector2D(x - other.x, y - other.y);
    }

    public Vector2D scale(double s) {
        return new Vector2D(x * s, y * s);
    }

    public double dot(Vector2D other) {
        return x * other.x + y * other.y;
    }

    public double cross(Vector2D other) {
        return x * other.y - y * other.x;
    }

    public double distanceTo(Vector2D other) {
        return subtract(other).magnitude();
    }

    public double angleTo(Vector2D other) {
        return Math.atan2(other.y - y, other.x - x);
    }

    public Vector2D rotate(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vector2D(x * cos - y * sin, x * sin + y * cos);
    }

    public Vector2D perpendicular() {
        return new Vector2D(-y, x);
    }

    public Vector2D clamp(double maxMagnitude) {
        double mag = magnitude();
        if (mag > maxMagnitude && mag > 0.0001) {
            return scale(maxMagnitude / mag);
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f)", x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vector2D)) return false;
        Vector2D other = (Vector2D) obj;
        return Math.abs(x - other.x) < 0.0001 && Math.abs(y - other.y) < 0.0001;
    }

    @Override
    public int hashCode() {
        long bits = Double.hashCode(x) ^ (Double.hashCode(y) * 31);
        return (int) bits;
    }
}
