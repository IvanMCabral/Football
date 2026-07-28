package com.footballmanager.application.service.simulation.detailed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24B: Shot xG model tests.
 * style modifier affects xG, goal resolution correlates with xG.
 *
 * (formation-specific xG modifier pipeline). All calls pass
 * {@code "4-4-2"} (the BALANCED_DEFAULT) so the unit tests verify the
 * non-formation multipliers in isolation. Formation effects are
 * covered by {@code ShotXgCalculatorFormationModifierTest}.
 */
class V24ShotXgModelTest {

    // when null/blank, so we explicitly pass "4-4-2" to keep test semantics stable.
    private static final String BASELINE_FORMATION = "4-4-2";

    private final ShotXgCalculator calc = new ShotXgCalculator();

    @Test
    void xgIsClampedToValidRange() {
        // Test minimum case (long range, low quality, high pressure, high gk)
        ShotQuality min = new ShotQuality(
                ShotLocation.LONG_RANGE, 0.1, 0.1, 0.9, 0.9, 0.85);
        double xgMin = calc.calculateXg(min, BASELINE_FORMATION);
        assertTrue(xgMin >= 0.01, "xG must be >= 0.01, got " + xgMin);

        // Test maximum case (six yard, high quality, no pressure, low gk, attacking)
        ShotQuality max = new ShotQuality(
                ShotLocation.SIX_YARD_BOX, 0.95, 0.95, 0.05, 0.1, 1.15);
        double xgMax = calc.calculateXg(max, BASELINE_FORMATION);
        assertTrue(xgMax <= 0.60, "xG must be <= 0.60, got " + xgMax);
    }

    @Test
    void differentLocationsProduceDifferentXg() {
        double sixYard = calc.calculateXg(new ShotQuality(
                ShotLocation.SIX_YARD_BOX, 0.5, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);
        double penaltyCenter = calc.calculateXg(new ShotQuality(
                ShotLocation.PENALTY_AREA_CENTER, 0.5, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);
        double outsideBox = calc.calculateXg(new ShotQuality(
                ShotLocation.OUTSIDE_BOX, 0.5, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);
        double longRange = calc.calculateXg(new ShotQuality(
                ShotLocation.LONG_RANGE, 0.5, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);

        assertTrue(sixYard > penaltyCenter,
                "sixYard xG > penaltyCenter xG");
        assertTrue(penaltyCenter > outsideBox,
                "penaltyCenter xG > outsideBox xG");
        assertTrue(outsideBox > longRange,
                "outsideBox xG > longRange xG");
    }

    @Test
    void attackingStyleIncreasesXg() {
        double attacking = calc.calculateXg(new ShotQuality(
                ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.5, 1.15), BASELINE_FORMATION);
        double defensive = calc.calculateXg(new ShotQuality(
                ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.5, 0.85), BASELINE_FORMATION);

        assertTrue(attacking > defensive,
                "attacking xG > defensive xG for same shot");
    }

    @Test
    void highDefensivePressureReducesXg() {
        double noPressure = calc.calculateXg(new ShotQuality(
                ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.05, 0.5, 1.0), BASELINE_FORMATION);
        double highPressure = calc.calculateXg(new ShotQuality(
                ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.9, 0.5, 1.0), BASELINE_FORMATION);

        assertTrue(noPressure > highPressure,
                "low pressure xG > high pressure xG");
    }

    @Test
    void goodGoalkeeperReducesXg() {
        double badGk = calc.calculateXg(new ShotQuality(
                ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.1, 1.0), BASELINE_FORMATION);
        double goodGk = calc.calculateXg(new ShotQuality(
                ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.9, 1.0), BASELINE_FORMATION);

        assertTrue(badGk > goodGk,
                "xG against bad GK > xG against good GK");
    }

    @Test
    void shooterQualityAffectsXg() {
        double lowQuality = calc.calculateXg(new ShotQuality(
                ShotLocation.PENALTY_AREA_CENTER, 0.2, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);
        double highQuality = calc.calculateXg(new ShotQuality(
                ShotLocation.PENALTY_AREA_CENTER, 0.9, 0.5, 0.5, 0.5, 1.0), BASELINE_FORMATION);

        assertTrue(highQuality > lowQuality,
                "high quality shooter xG > low quality xG");
    }

    @Test
    void allLocationsProduceXgInRange() {
        ShotLocation[] locations = ShotLocation.values();
        for (ShotLocation loc : locations) {
            ShotQuality q = new ShotQuality(loc, 0.5, 0.5, 0.5, 0.5, 1.0);
            double xg = calc.calculateXg(q, BASELINE_FORMATION);
            assertTrue(xg >= 0.01 && xg <= 0.60,
                    "xG for " + loc + " must be in [0.01, 0.60], got " + xg);
        }
    }
}
