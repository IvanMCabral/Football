package com.footballmanager.application.service.simulation.detailed;

import java.util.Objects;
import java.util.Random;

/**
 *
 * <p>Generates ShotCoordinate from ShotLocation using a passed Random for determinism.
 */
public final class ShotCoordinateGenerator {

    /**
     * Generate a shot coordinate for the given location using the provided Random.
     * Deterministic: same location + same Random state = same coordinate.
     */
    public ShotCoordinate generate(ShotLocation location, Random random) {
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

        return new ShotCoordinate(x, y, location);
    }

    /**
     * Generate a wide shot coordinate biased to one flank. Intended for
     * tactical calibration paths where the style explicitly loads one side.
     */
    public ShotCoordinate generateWideFlank(boolean left, Random random) {
        Objects.requireNonNull(random, "random must not be null");
        double x = randomInRange(random, 83.0, 93.0);
        double y = left
                ? randomInRange(random, 18.0, 42.0)
                : randomInRange(random, 58.0, 82.0);
        return new ShotCoordinate(x, y, ShotLocation.PENALTY_AREA_WIDE);
    }

    /**
     * Generate a penalty kick coordinate (fixed central position near penalty spot).
     */
    public ShotCoordinate penalty(Random random) {
        Objects.requireNonNull(random, "random must not be null");
        double x = randomInRange(random, 88.0, 92.0);
        double y = randomInRange(random, 46.0, 54.0);
        return new ShotCoordinate(x, y, ShotLocation.SIX_YARD_BOX);
    }

    private static double randomInRange(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}
