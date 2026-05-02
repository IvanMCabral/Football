package com.footballmanager.domain.simulation.v32.math;

/**
 * Immutable 3D vector for V32 simulation.
 */
public final class Vector3D {

    public final double x;
    public final double y;
    public final double z;

    public Vector3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vector3D zero() {
        return new Vector3D(0, 0, 0);
    }

    public static Vector3D from2D(Vector2D v, double z) {
        return new Vector3D(v.x, v.y, z);
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double magnitudeSquared() {
        return x * x + y * y + z * z;
    }

    public Vector3D normalize() {
        double mag = magnitude();
        if (mag < 0.0001) return zero();
        return new Vector3D(x / mag, y / mag, z / mag);
    }

    public Vector3D add(Vector3D other) {
        return new Vector3D(x + other.x, y + other.y, z + other.z);
    }

    public Vector3D subtract(Vector3D other) {
        return new Vector3D(x - other.x, y - other.y, z - other.z);
    }

    public Vector3D scale(double s) {
        return new Vector3D(x * s, y * s, z * s);
    }

    public double dot(Vector3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3D cross(Vector3D other) {
        return new Vector3D(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    public double distanceTo(Vector3D other) {
        return subtract(other).magnitude();
    }

    public Vector2D to2D() {
        return new Vector2D(x, y);
    }

    public Vector3D withZ(double newZ) {
        return new Vector3D(x, y, newZ);
    }

    public Vector3D clamp(double maxMagnitude) {
        double mag = magnitude();
        if (mag > maxMagnitude && mag > 0.0001) {
            return scale(maxMagnitude / mag);
        }
        return this;
    }

    /** Projects this 3D vector onto the XZ plane (ground plane). */
    public Vector2D projectXZ() {
        return new Vector2D(x, z);
    }

    /** Projects this 3D vector onto the XY plane. */
    public Vector2D projectXY() {
        return new Vector2D(x, y);
    }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f, %.3f)", x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vector3D)) return false;
        Vector3D other = (Vector3D) obj;
        return Math.abs(x - other.x) < 0.0001 &&
               Math.abs(y - other.y) < 0.0001 &&
               Math.abs(z - other.z) < 0.0001;
    }

    @Override
    public int hashCode() {
        long bits = Double.hashCode(x) ^ (Double.hashCode(y) * 31) ^ (Double.hashCode(z) * 17);
        return (int) bits;
    }
}
