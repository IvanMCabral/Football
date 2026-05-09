package com.footballmanager.application.service.simulation.v24;

import java.util.Objects;
import java.util.Random;

/**
 * V24D3A: Deterministic shot coordinate generator.
 *
 * <p>Generates V24ShotCoordinate from V24ShotLocation using a passed Random for determinism.
 * All ranges match the V24D3 plan specification.
 */
public final class V24ShotCoordinateGenerator {

    /**
     * Generate a shot coordinate for the given location using the provided Random.
     * Deterministic: same location + same Random state = same coordinate.
     */
    public V24ShotCoordinate generate(V24ShotLocation location, Random random) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(random, "random must not be null");

        double x;
        double y;

        switch (location) {
            case SIX_YARD_BOX:
                x = randomInRange(random, 94.0, 99.0);
                y = randomInRange(random, 42.0, 58.0);
                break;
            case PENALTY_AREA_CENTER:
                x = randomInRange(random, 83.0, 95.0);
                y = randomInRange(random, 30.0, 70.0);
                break;
            case PENALTY_AREA_WIDE:
                x = randomInRange(random, 83.0, 93.0);
                y = randomInRange(random, 18.0, 82.0);
                break;
            case OUTSIDE_BOX:
                x = randomInRange(random, 60.0, 84.0);
                y = randomInRange(random, 15.0, 85.0);
                break;
            case LONG_RANGE:
                x = randomInRange(random, 35.0, 62.0);
                y = randomInRange(random, 10.0, 90.0);
                break;
            default:
                // Defensive fallback for any future enum values
                x = randomInRange(random, 60.0, 84.0);
                y = randomInRange(random, 15.0, 85.0);
                break;
        }

        return new V24ShotCoordinate(x, y, location);
    }

    /**
     * Generate a penalty kick coordinate (fixed central position near penalty spot).
     */
    public V24ShotCoordinate penalty(Random random) {
        Objects.requireNonNull(random, "random must not be null");
        double x = randomInRange(random, 88.0, 92.0);
        double y = randomInRange(random, 46.0, 54.0);
        return new V24ShotCoordinate(x, y, V24ShotLocation.SIX_YARD_BOX);
    }

    private static double randomInRange(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}
