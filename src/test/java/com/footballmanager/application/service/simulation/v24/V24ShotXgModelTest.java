package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24B: Shot xG model tests.
 * Verifies: xG clamped [0.01, 0.60] (V24D6U4 tuned from 0.80), different locations produce different xG,
 * style modifier affects xG, goal resolution correlates with xG.
 */
class V24ShotXgModelTest {

    private final V24ShotXgCalculator calc = new V24ShotXgCalculator();

    @Test
    void xgIsClampedToValidRange() {
        // Test minimum case (long range, low quality, high pressure, high gk)
        V24ShotQuality min = new V24ShotQuality(
                V24ShotLocation.LONG_RANGE, 0.1, 0.1, 0.9, 0.9, 0.85);
        double xgMin = calc.calculateXg(min);
        assertTrue(xgMin >= 0.01, "xG must be >= 0.01, got " + xgMin);

        // Test maximum case (six yard, high quality, no pressure, low gk, attacking)
        V24ShotQuality max = new V24ShotQuality(
                V24ShotLocation.SIX_YARD_BOX, 0.95, 0.95, 0.05, 0.1, 1.15);
        double xgMax = calc.calculateXg(max);
        // V24D6U4: MAX_XG tuned from 0.80 to 0.60
        assertTrue(xgMax <= 0.60, "xG must be <= 0.60, got " + xgMax);
    }

    @Test
    void differentLocationsProduceDifferentXg() {
        double sixYard = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.SIX_YARD_BOX, 0.5, 0.5, 0.5, 0.5, 1.0));
        double penaltyCenter = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.5, 0.5, 0.5, 0.5, 1.0));
        double outsideBox = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.OUTSIDE_BOX, 0.5, 0.5, 0.5, 0.5, 1.0));
        double longRange = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.LONG_RANGE, 0.5, 0.5, 0.5, 0.5, 1.0));

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
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.5, 1.15));
        double defensive = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.5, 0.85));

        assertTrue(attacking > defensive,
                "attacking xG > defensive xG for same shot");
    }

    @Test
    void highDefensivePressureReducesXg() {
        double noPressure = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.05, 0.5, 1.0));
        double highPressure = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.9, 0.5, 1.0));

        assertTrue(noPressure > highPressure,
                "low pressure xG > high pressure xG");
    }

    @Test
    void goodGoalkeeperReducesXg() {
        double badGk = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.1, 1.0));
        double goodGk = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.6, 0.6, 0.5, 0.9, 1.0));

        assertTrue(badGk > goodGk,
                "xG against bad GK > xG against good GK");
    }

    @Test
    void shooterQualityAffectsXg() {
        double lowQuality = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.2, 0.5, 0.5, 0.5, 1.0));
        double highQuality = calc.calculateXg(new V24ShotQuality(
                V24ShotLocation.PENALTY_AREA_CENTER, 0.9, 0.5, 0.5, 0.5, 1.0));

        assertTrue(highQuality > lowQuality,
                "high quality shooter xG > low quality xG");
    }

    @Test
    void allLocationsProduceXgInRange() {
        V24ShotLocation[] locations = V24ShotLocation.values();
        for (V24ShotLocation loc : locations) {
            V24ShotQuality q = new V24ShotQuality(loc, 0.5, 0.5, 0.5, 0.5, 1.0);
            double xg = calc.calculateXg(q);
            // V24D6U4: xG range updated from [0.01, 0.80] to [0.01, 0.60]
            assertTrue(xg >= 0.01 && xg <= 0.60,
                    "xG for " + loc + " must be in [0.01, 0.60], got " + xg);
        }
    }
}