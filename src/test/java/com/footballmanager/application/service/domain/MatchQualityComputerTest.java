package com.footballmanager.application.service.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1B: MatchQualityComputer unit tests.
 * Verifies lambda computation matches the formula used by MatchEngineImpl.
 */
class MatchQualityComputerTest {

    @Test
    void shouldComputeLambdasEqualOvr() {
        var l = MatchQualityComputer.computeLambdas(75, 75);

        // totalLambda = 2.60 (no imbalance boost)
        // homeShare = 0.52
        // homeLambda = 2.60 * 0.52 = 1.352
        // awayLambda = 2.60 * 0.48 = 1.248
        assertEquals(1.352, l.homeLambda(), 0.001);
        assertEquals(1.248, l.awayLambda(), 0.001);
        assertEquals(2.600, l.totalLambda(), 0.001);
        assertEquals(0.52, l.homeShare(), 0.001);

        assertEquals(2.600, l.totalXg(), 0.001);
        assertEquals(1.352, l.homeXg(), 0.001);
        assertEquals(1.248, l.awayXg(), 0.001);
    }

    @Test
    void shouldComputeLambdasSlightFavorite() {
        var l = MatchQualityComputer.computeLambdas(80, 70);

        // ovrDiff = 10, imbalanceBoost = 0.12, totalLambda = 2.72
        // homeShare = 0.52 + 10/220 = 0.52 + 0.04545 = 0.56545
        // homeLambda = 2.72 * 0.56545 ≈ 1.538
        // awayLambda = 2.72 * 0.43455 ≈ 1.182
        assertEquals(1.538, l.homeLambda(), 0.01);
        assertEquals(1.182, l.awayLambda(), 0.01);
        assertEquals(2.72, l.totalLambda(), 0.01);
        assertTrue(l.homeShare() > 0.52);
    }

    @Test
    void shouldComputeLambdasStrongFavorite() {
        var l = MatchQualityComputer.computeLambdas(90, 60);

        // ovrDiff = 30, imbalanceBoost = 0.36, totalLambda = 2.96
        // homeShare = 0.52 + 30/220 = 0.52 + 0.1364 = 0.6564
        // homeLambda = 2.96 * 0.6564 ≈ 1.943
        // awayLambda = 2.96 * 0.3436 ≈ 1.017
        assertEquals(1.943, l.homeLambda(), 0.01);
        assertEquals(1.017, l.awayLambda(), 0.01);
        assertEquals(2.96, l.totalLambda(), 0.01);
        assertTrue(l.homeShare() > 0.60);
    }

    @Test
    void shouldClampLambdasAtUpperBound() {
        // Very large OVR diff — totalLambda should clamp to 3.05
        var l = MatchQualityComputer.computeLambdas(100, 40);

        // ovrDiff = 60, imbalanceBoost = 0.72, baseTotal = 3.32 > 3.05 max
        assertEquals(3.05, l.totalLambda(), 0.001);
        assertEquals(0.75, l.homeShare(), 0.001); // clamped to max
    }

    @Test
    void shouldClampLambdasAtLowerBound() {
        // Very small OVR diff — totalLambda should clamp to 2.3
        var l = MatchQualityComputer.computeLambdas(52, 48);

        // ovrDiff = 4, imbalanceBoost = 0.048, baseTotal = 2.648 > 2.3, no clamp
        // But with reverse: home is weaker, share clamped to 0.25 min
        var l2 = MatchQualityComputer.computeLambdas(40, 100);
        // ovrDiff = -60, imbalanceBoost = 0.72, baseTotal = 3.32 > 3.05 clamp
        // homeShare = 0.52 - 60/220 = 0.52 - 0.2727 = 0.2473 < 0.25 → clamp to 0.25
        assertEquals(0.25, l2.homeShare(), 0.001);
    }

    @Test
    void shouldReturnFinitePositiveValues() {
        var l = MatchQualityComputer.computeLambdas(75, 75);
        assertFalse(Double.isNaN(l.homeLambda()));
        assertFalse(Double.isInfinite(l.homeLambda()));
        assertFalse(Double.isNaN(l.awayLambda()));
        assertFalse(Double.isInfinite(l.awayLambda()));
        assertFalse(Double.isNaN(l.totalXg()));
        assertFalse(Double.isInfinite(l.totalXg()));
    }
}