package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D3A: Tests for V24ShotCoordinate and V24ShotCoordinateGenerator.
 * Validates coordinate bounds, derived values, determinism, and validity.
 */
class V24ShotCoordinateTest {

    private final V24ShotCoordinateGenerator generator = new V24ShotCoordinateGenerator();

    // ========== V24ShotCoordinate basic validity ==========

    @Test
    void coordinateStaysInsidePitch() {
        for (V24ShotLocation loc : V24ShotLocation.values()) {
            for (int seed = 1; seed <= 10; seed++) {
                var coord = generator.generate(loc, new Random(seed));
                assertTrue(coord.x() >= 0.0 && coord.x() <= 100.0,
                        "x must be [0,100] for " + loc + " seed " + seed + ": x=" + coord.x());
                assertTrue(coord.y() >= 0.0 && coord.y() <= 100.0,
                        "y must be [0,100] for " + loc + " seed " + seed + ": y=" + coord.y());
            }
        }
    }

    @Test
    void sixYardBoxCoordinateIsCloseToGoal() {
        var coord = generator.generate(V24ShotLocation.SIX_YARD_BOX, new Random(42));
        assertTrue(coord.distanceToGoal() < 20.0,
                "SIX_YARD_BOX should be close to goal, distance=" + coord.distanceToGoal());
        assertTrue(coord.insideBox(),
                "SIX_YARD_BOX should be inside box");
    }

    @Test
    void longRangeCoordinateIsFartherThanPenaltyArea() {
        var longRange = generator.generate(V24ShotLocation.LONG_RANGE, new Random(1));
        var penaltyArea = generator.generate(V24ShotLocation.PENALTY_AREA_CENTER, new Random(1));
        assertTrue(longRange.distanceToGoal() > penaltyArea.distanceToGoal(),
                "LONG_RANGE should be farther than PENALTY_AREA_CENTER: "
                        + longRange.distanceToGoal() + " vs " + penaltyArea.distanceToGoal());
    }

    @Test
    void insideBoxFlagTrueWhenInPenaltyArea() {
        // Direct construction: x=90, y=50 is inside penalty area
        var coord = new V24ShotCoordinate(90.0, 50.0, V24ShotLocation.PENALTY_AREA_CENTER);
        assertTrue(coord.insideBox(), "x=90, y=50 should be inside penalty area");
    }

    @Test
    void insideBoxFlagFalseWhenOutsideBox() {
        // Direct construction: x=70, y=50 is outside penalty area
        var coord = new V24ShotCoordinate(70.0, 50.0, V24ShotLocation.OUTSIDE_BOX);
        assertFalse(coord.insideBox(), "x=70, y=50 should be outside penalty area");
    }

    @Test
    void sameSeedProducesSameCoordinate() {
        var loc = V24ShotLocation.PENALTY_AREA_CENTER;
        var rand1 = new Random(12345);
        var rand2 = new Random(12345);

        var coord1 = generator.generate(loc, rand1);
        var coord2 = generator.generate(loc, rand2);

        assertEquals(coord1.x(), coord2.x(), "x must be identical for same seed");
        assertEquals(coord1.y(), coord2.y(), "y must be identical for same seed");
        assertEquals(coord1.distanceToGoal(), coord2.distanceToGoal(), "distance must be identical for same seed");
        assertEquals(coord1.angleToGoal(), coord2.angleToGoal(), "angle must be identical for same seed");
        assertEquals(coord1.location(), coord2.location());
    }

    @Test
    void differentSeedsProduceValidCoordinates() {
        for (V24ShotLocation loc : V24ShotLocation.values()) {
            var coord1 = generator.generate(loc, new Random(1));
            var coord2 = generator.generate(loc, new Random(2));
            assertNotNull(coord1);
            assertNotNull(coord2);
            // Both must be valid within pitch
            assertTrue(coord1.x() >= 0 && coord1.x() <= 100);
            assertTrue(coord1.y() >= 0 && coord1.y() <= 100);
            assertTrue(coord2.x() >= 0 && coord2.x() <= 100);
            assertTrue(coord2.y() >= 0 && coord2.y() <= 100);
        }
    }

    @Test
    void distanceAndAngleAreFinite() {
        for (V24ShotLocation loc : V24ShotLocation.values()) {
            var coord = generator.generate(loc, new Random(99));
            assertTrue(Double.isFinite(coord.distanceToGoal()),
                    "distanceToGoal must be finite for " + loc);
            assertTrue(Double.isFinite(coord.angleToGoal()),
                    "angleToGoal must be finite for " + loc);
        }
    }

    @Test
    void rejectsInvalidX() {
        assertThrows(IllegalArgumentException.class, () ->
                new V24ShotCoordinate(-0.1, 50.0, V24ShotLocation.PENALTY_AREA_CENTER));
        assertThrows(IllegalArgumentException.class, () ->
                new V24ShotCoordinate(100.1, 50.0, V24ShotLocation.PENALTY_AREA_CENTER));
        assertThrows(IllegalArgumentException.class, () ->
                new V24ShotCoordinate(Double.NaN, 50.0, V24ShotLocation.PENALTY_AREA_CENTER));
        assertThrows(IllegalArgumentException.class, () ->
                new V24ShotCoordinate(Double.POSITIVE_INFINITY, 50.0, V24ShotLocation.PENALTY_AREA_CENTER));
    }

    @Test
    void rejectsInvalidY() {
        assertThrows(IllegalArgumentException.class, () ->
                new V24ShotCoordinate(50.0, -0.1, V24ShotLocation.PENALTY_AREA_CENTER));
        assertThrows(IllegalArgumentException.class, () ->
                new V24ShotCoordinate(50.0, 100.1, V24ShotLocation.PENALTY_AREA_CENTER));
        assertThrows(IllegalArgumentException.class, () ->
                new V24ShotCoordinate(50.0, Double.NaN, V24ShotLocation.PENALTY_AREA_CENTER));
        assertThrows(IllegalArgumentException.class, () ->
                new V24ShotCoordinate(50.0, Double.POSITIVE_INFINITY, V24ShotLocation.PENALTY_AREA_CENTER));
    }

    @Test
    void rejectsNullLocation() {
        assertThrows(NullPointerException.class, () ->
                generator.generate(null, new Random(1)));
        assertThrows(NullPointerException.class, () ->
                new V24ShotCoordinate(50.0, 50.0, null));
    }

    @Test
    void penaltyLocationIsCentral() {
        var penalty = generator.penalty(new Random(42));
        assertTrue(penalty.x() >= 88.0 && penalty.x() <= 92.0,
                "Penalty x should be near penalty spot: " + penalty.x());
        assertTrue(penalty.y() >= 46.0 && penalty.y() <= 54.0,
                "Penalty y should be central: " + penalty.y());
        assertTrue(penalty.insideBox(),
                "Penalty kick should be inside box");
    }

    @Test
    void generatedLocationIsPreserved() {
        for (V24ShotLocation loc : V24ShotLocation.values()) {
            var coord = generator.generate(loc, new Random(7));
            assertEquals(loc, coord.location(),
                    "Generated coordinate must preserve requested location");
        }
    }

    @Test
    void allLocationsGenerateValidCoordinates() {
        for (V24ShotLocation loc : V24ShotLocation.values()) {
            var coord = generator.generate(loc, new Random(42));
            assertNotNull(coord.x());
            assertNotNull(coord.y());
            assertNotNull(coord.location());
            assertTrue(Double.isFinite(coord.distanceToGoal()));
            assertTrue(Double.isFinite(coord.angleToGoal()));
        }
    }

    // ========== Derived value accuracy ==========

    @Test
    void insideBoxBoundaryConditions() {
        // Exactly on boundary: x=83, y=21 and y=79 should be inside
        var onBoundaryLeft = new V24ShotCoordinate(83.0, 21.0, V24ShotLocation.PENALTY_AREA_CENTER);
        var onBoundaryRight = new V24ShotCoordinate(83.0, 79.0, V24ShotLocation.PENALTY_AREA_CENTER);
        assertTrue(onBoundaryLeft.insideBox(), "x=83, y=21 should be inside");
        assertTrue(onBoundaryRight.insideBox(), "x=83, y=79 should be inside");

        // Just outside: x=82.9 or y=20.9 or y=79.1
        var justOutsideLeft = new V24ShotCoordinate(82.9, 50.0, V24ShotLocation.PENALTY_AREA_CENTER);
        var justOutsideLow = new V24ShotCoordinate(90.0, 20.9, V24ShotLocation.PENALTY_AREA_CENTER);
        var justOutsideHigh = new V24ShotCoordinate(90.0, 79.1, V24ShotLocation.PENALTY_AREA_CENTER);
        assertFalse(justOutsideLeft.insideBox(), "x=82.9 should be outside");
        assertFalse(justOutsideLow.insideBox(), "y=20.9 should be outside");
        assertFalse(justOutsideHigh.insideBox(), "y=79.1 should be outside");
    }

    @Test
    void distanceCalculationIsCorrect() {
        // Shot at x=100, y=50 (goal line) -> distance should be 0
        var atGoal = new V24ShotCoordinate(100.0, 50.0, V24ShotLocation.SIX_YARD_BOX);
        assertEquals(0.0, atGoal.distanceToGoal(), 0.001);

        // Shot at x=90, y=50 -> distance should be 10
        var tenMeters = new V24ShotCoordinate(90.0, 50.0, V24ShotLocation.SIX_YARD_BOX);
        assertEquals(10.0, tenMeters.distanceToGoal(), 0.001);

        // Shot at x=80, y=50 -> distance should be 20
        var twentyMeters = new V24ShotCoordinate(80.0, 50.0, V24ShotLocation.PENALTY_AREA_CENTER);
        assertEquals(20.0, twentyMeters.distanceToGoal(), 0.001);
    }

    @Test
    void sixYardBoxRangeSupportsNarrowBand() {
        // SIX_YARD_BOX y range is [42, 58] which is narrow
        // Verify we can generate coordinates in this narrow band
        for (int seed = 1; seed <= 20; seed++) {
            var coord = generator.generate(V24ShotLocation.SIX_YARD_BOX, new Random(seed));
            assertTrue(coord.y() >= 42.0 && coord.y() <= 58.0,
                    "SIX_YARD_BOX y should be in [42,58], got " + coord.y());
        }
    }
}
