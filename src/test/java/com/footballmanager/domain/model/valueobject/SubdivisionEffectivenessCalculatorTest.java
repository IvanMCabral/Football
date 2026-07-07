package com.footballmanager.domain.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D99.16-BACK: unit tests for {@link SubdivisionEffectivenessCalculator}.
 *
 * <p>Validates the geometry-aware effectiveness falloff introduced to
 * give the lineup ratings panel subdivision-level feedback (so within-
 * zone drag-and-drop produces visible changes instead of the V25D99.15
 * frozen-snapshot bug Ivan reported: "mover un mediocampista central un
 * slot hacia el centro no cambia nada").
 *
 * <p>Three test groups:
 * <ul>
 *   <li><b>Hard caps</b> &mdash; GK in non-GK slot stays 0.0; non-GK in
 *       GK slot stays 0.0 (propagates from
 *       {@link PositionEffectivenessCalculator}).</li>
 *   <li><b>Backward compat (NaN coords)</b> &mdash; any NaN coord
 *       reverts to the zone-only lookup, matching pre-V25D99.16 math.</li>
 *   <li><b>Distance penalty</b> &mdash; within the same zone, varying
 *       xPct shifts the effectiveness by a measurable amount. Across
 *       zones, the zone-level base (0.0-1.0 lookup) still dominates.</li>
 * </ul>
 */
@DisplayName("SubdivisionEffectivenessCalculator — V25D99.16 geometry-aware effectiveness")
class SubdivisionEffectivenessCalculatorTest {

    private static final double EPS = 0.0001;

    @Test
    @DisplayName("GK in GK slot = 1.0 (centered, no penalty)")
    void gkInGkSlot_unity() {
        double eff = SubdivisionEffectivenessCalculator.effectiveness(
                "GK", 50.0, 93.0, "GK");
        assertEquals(1.0, eff, EPS);
    }

    @Test
    @DisplayName("GK in non-GK slot = 0.0 (hard cap, matches legacy)")
    void gkInNonGkSlot_zero() {
        double eff = SubdivisionEffectivenessCalculator.effectiveness(
                "GK", 50.0, 50.0, "MID");
        assertEquals(0.0, eff, EPS);
    }

    @Test
    @DisplayName("CB in non-GK GK slot = 0.0 (hard cap)")
    void cbInGkSlot_zero() {
        double eff = SubdivisionEffectivenessCalculator.effectiveness(
                "CB", 50.0, 93.0, "GK");
        assertEquals(0.0, eff, EPS);
    }

    @Test
    @DisplayName("NaN coords → legacy zone-only math (backward compat)")
    void nanCoords_legacyFallback() {
        // CB in DEF slot, NaN coords: expected = base zone eff = 1.0.
        double cb = SubdivisionEffectivenessCalculator.effectiveness(
                "CB", Double.NaN, Double.NaN, "DEF");
        assertEquals(1.0, cb, EPS);

        // CB in MID slot, NaN coords: expected = base zone eff = 0.8.
        double cbMid = SubdivisionEffectivenessCalculator.effectiveness(
                "CB", Double.NaN, Double.NaN, "MID");
        assertEquals(0.8, cbMid, EPS);

        // CB in ATT slot, NaN coords: expected = base zone eff = 0.4.
        double cbAtt = SubdivisionEffectivenessCalculator.effectiveness(
                "CB", Double.NaN, Double.NaN, "ATT");
        assertEquals(0.4, cbAtt, EPS);
    }

    @Test
    @DisplayName("CB at CB slot ideal coords → ~1.0 (full bonus)")
    void cbAtCentralCbSlot_nearUnity() {
        // CB ideal (50, 83). Slot at (49.95, 83) — distance ~0.05.
        // Penalty ~0.00015 * 0.30 = ~0.000045 → eff ~ 0.99995.
        double eff = SubdivisionEffectivenessCalculator.effectiveness(
                "CB", 49.95, 83.0, "DEF");
        assertTrue(eff > 0.99, "CB at central slot should be near 1.0, got " + eff);
    }

    @Test
    @DisplayName("CB at opposite wing slot → significantly lower than 1.0")
    void cbAtOppositeWing_lower() {
        // CB ideal (50, 83). Slot at (94.35, 83) — V25D94 extreme right.
        // distance = ~44.35. penalty = 0.30 * 0.4435 = 0.133.
        // eff = 1.0 * (1 - 0.133) = 0.867.
        double eff = SubdivisionEffectivenessCalculator.effectiveness(
                "CB", 94.35, 83.0, "DEF");
        assertEquals(0.867, eff, 0.01);
    }

    @Test
    @DisplayName("Within-zone horizontal drag changes effectiveness measurably")
    void withinZoneDrag_changes() {
        // CM in central midfield (S17-2 row, ~50, 60). CM ideal (50, 60).
        // Distance 0 → penalty 0 → eff 1.0.
        double center = SubdivisionEffectivenessCalculator.effectiveness(
                "CM", 49.95, 60.0, "MID");

        // CM dragged to left M1 row (S17-1, ~38.85, 60). distance ~11.15.
        // penalty = 0.30 * 0.1115 = 0.0334 → eff = 0.9666.
        double left = SubdivisionEffectivenessCalculator.effectiveness(
                "CM", 38.85, 60.0, "MID");

        // CM dragged further left to LM slot (~16.65, 60). distance ~33.35.
        // penalty = 0.30 * 0.3335 = 0.10 → eff = 0.90.
        double farLeft = SubdivisionEffectivenessCalculator.effectiveness(
                "CM", 16.65, 60.0, "MID");

        // Same zone (MID) for all three, so zone base is identical (1.0).
        // The geometry penalty is the ONLY thing that varies.
        assertTrue(center > left, "central should beat left: " + center + " > " + left);
        assertTrue(left > farLeft, "left should beat far-left: " + left + " > " + farLeft);

        assertTrue(Math.abs(center - left) > 0.02,
                "center vs left diff should be > 0.02 (visible): " + Math.abs(center - left));
        assertTrue(Math.abs(center - farLeft) > 0.05,
                "center vs far-left diff should be > 0.05 (clearly visible): "
                        + Math.abs(center - farLeft));
    }

    @Test
    @DisplayName("Unknown natural position falls back to base (backward compat)")
    void unknownNatural_legacyFallback() {
        // "FUTURE_POS" is not in the IDEAL_COORDS map, so geometry
        // penalty is skipped, and the lookup returns base eff.
        double eff = SubdivisionEffectivenessCalculator.effectiveness(
                "FUTURE_POS", 50.0, 60.0, "MID");
        // PositionEffectivenessCalculator returns 1.0 for unknown
        // natural (backward compat). SubdivisionCalculator inherits.
        assertEquals(1.0, eff, EPS);
    }

    @Test
    @DisplayName("Floor: refined effectiveness never below 0.05 (unless base = 0)")
    void floorHolds() {
        // Far corner drag should still leave a small positive contribution.
        // CB at extreme (94.35, 17) — distance to CB ideal (50, 83) = sqrt(44² + 66²) ≈ 79.5.
        // distance saturates at 100, so penalty = 0.30. eff = 1.0 * 0.7 = 0.7.
        double eff = SubdivisionEffectivenessCalculator.effectiveness(
                "CB", 94.35, 17.0, "DEF");
        assertTrue(eff >= 0.05, "floor should hold: " + eff);
        assertTrue(eff <= 1.0, "should never exceed base: " + eff);
    }

    @Test
    @DisplayName("idealCoordsFor() returns IDEAL_COORDS entries (3-cat + 5-cat fallback)")
    void idealCoordsLookup() {
        assertArrayEquals(new double[]{50.0, 83.0}, SubdivisionEffectivenessCalculator.idealCoordsFor("CB"));
        assertArrayEquals(new double[]{50.0, 93.0}, SubdivisionEffectivenessCalculator.idealCoordsFor("GK"));
        assertArrayEquals(new double[]{50.0, 60.0}, SubdivisionEffectivenessCalculator.idealCoordsFor("CM"));
        // 5-cat fallback works too.
        assertArrayEquals(new double[]{50.0, 83.0}, SubdivisionEffectivenessCalculator.idealCoordsFor("DEF"));
        // Unknown returns null (caller skips geometry penalty).
        assertNull(SubdivisionEffectivenessCalculator.idealCoordsFor("FOO"));
    }
}
