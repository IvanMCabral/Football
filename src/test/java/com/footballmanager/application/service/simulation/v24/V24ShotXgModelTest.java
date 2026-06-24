package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24B: Shot xG model tests.
 * Verifies: xG clamped [0.01, 0.60] (V24D6U4 tuned from 0.80), different locations produce different xG,
 * style modifier affects xG, goal resolution correlates with xG.
 *
 * <p><b>V25D25 update:</b> {@code calculateXg} now takes a formation argument
 * (formation-specific xG modifier pipeline). All calls pass
 * {@code "4-4-2"} (the BALANCED_DEFAULT) so the unit tests verify the
 * non-formation multipliers in isolation. Formation effects are
 * covered by {@code V24ShotXgCalculatorFormationModifierTest}.
 */
class V24ShotXgModelTest {

    // V25D25: formation default for unit tests — parser falls back to 4-4-2 (modifier=1.03)
    // when null/blank, so we explicitly pass "4-4-2" to keep test semantics stable.
    private static final String BASELINE_FORMATION = "4-4-2";

    private final V24ShotXgCalculator calc = new V24ShotXgCalculator();

    @Test
    void xgIsClampedToValidRange() {
        // Test minimum case (long range, low quality, high pressure, high gk)
        V24ShotQuality min = new V24ShotQuality(
                V24ShotLocation.LONG_RANGE, 0.1, 0.1, 0.9, 0.9, 0.85);
        double xgMin = calc.calculateXg(min, BASELINE_FORMATION);
        assertTrue(xgMin >= 0.01, "xG must be >= 0.01, got " + xgMin);

        // Test maximum case (six yard, high quality, no pressure, low gk, attacking)
        V24ShotQuality max = new V24ShotQuality(
                V24ShotLocation.SIX_YARD_BOX, 0.95, 0.95, 0.05, 0.1, 1.15);
        double xgMax = calc.calculateXg(max, BASELINE_FORMATION);
        // V24D6U4: MAX_XG tuned from 0.80 to 0.60
        assertTrue(xgMax <= 0.60, "xG must be <= 0.60, got " + xgMax);
    }

    @Test
    void differentLocationsProduceDifferentXg() {
        double sixYard = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.SIX_YARD_BOX, 0.5, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);
        double penaltyCenter = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.5, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);
        double outsideBox = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.OUTSIDE_BOX, 0.5, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);
        double longRange = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.LONG_RANGE, 0.5, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);

        assertTrue(sixYard > penaltyCenter,
                "sixYard xG > penaltyCenter xG");
        assertTrue(penaltyCenter > outsideBox,
                "penaltyCenter xG > outsideBox xG");
        assertTrue(outsideBox > longRange,
                "outsideBox xG > longRange xG");
    }

    @Test
    void attackingStyleIncreasesXg() {
        double attacking = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.5, 1.15), BASELINE_FORMATION);
        double defensive = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.5, 0.85), BASELINE_FORMATION);

        assertTrue(attacking > defensive,
                "attacking xG > defensive xG for same shot");
    }

    @Test
    void highDefensivePressureReducesXg() {
        double noPressure = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.05, 0.5, 1.0), BASELINE_FORMATION);
        double highPressure = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.9, 0.5, 1.0), BASELINE_FORMATION);

        assertTrue(noPressure > highPressure,
                "low pressure xG > high pressure xG");
    }

    @Test
    void goodGoalkeeperReducesXg() {
        double badGk = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.1, 1.0), BASELINE_FORMATION);
        double goodGk = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.9, 1.0), BASELINE_FORMATION);

        assertTrue(badGk > goodGk,
                "xG against bad GK > xG against good GK");
    }

    @Test
    void shooterQualityAffectsXg() {
        double lowQuality = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.2, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);
        double highQuality = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.9, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);

        assertTrue(highQuality > lowQuality,
                "high quality shooter xG > low quality xG");
    }

    @Test
    void allLocationsProduceXgInRange() {
        V24ShotLocation[] locations = V24ShotLocation.values();
        for (V24ShotLocation loc : locations) {
            V24ShotQuality q = new V24ShotQuality(loc, 0.5, 0.5, 0.5, 0.5, 1.0);
            double xg = calc.calculateXg(q, BASELINE_FORMATION);
            // V24D6U4: xG range updated from [0.01, 0.80] to [0.01, 0.60]
            assertTrue(xg >= 0.01 && xg <= 0.60,
                    "xG for " + loc + " must be in [0.01, 0.60], got " + xg);
        }
    }
}
