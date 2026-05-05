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

    // ========== Phase 6A: Style-aware computation tests ==========

    @Test
    void balancedStyleEqualsBaseline_75_75() {
        var baseline = MatchQualityComputer.computeLambdas(75, 75);
        var styled = MatchQualityComputer.computeLambdas(75, 75, TeamStyle.BALANCED, TeamStyle.BALANCED);
        assertEquals(baseline.homeLambda(), styled.homeLambda(), 1e-9, "homeLambda");
        assertEquals(baseline.awayLambda(), styled.awayLambda(), 1e-9, "awayLambda");
        assertEquals(baseline.totalLambda(), styled.totalLambda(), 1e-9, "totalLambda");
        assertEquals(baseline.homeShare(), styled.homeShare(), 1e-9, "homeShare");
    }

    @Test
    void balancedStyleEqualsBaseline_80_70() {
        var baseline = MatchQualityComputer.computeLambdas(80, 70);
        var styled = MatchQualityComputer.computeLambdas(80, 70, TeamStyle.BALANCED, TeamStyle.BALANCED);
        assertEquals(baseline.homeLambda(), styled.homeLambda(), 1e-9, "homeLambda");
        assertEquals(baseline.awayLambda(), styled.awayLambda(), 1e-9, "awayLambda");
        assertEquals(baseline.totalLambda(), styled.totalLambda(), 1e-9, "totalLambda");
        assertEquals(baseline.homeShare(), styled.homeShare(), 1e-9, "homeShare");
    }

    @Test
    void balancedStyleEqualsBaseline_90_60() {
        var baseline = MatchQualityComputer.computeLambdas(90, 60);
        var styled = MatchQualityComputer.computeLambdas(90, 60, TeamStyle.BALANCED, TeamStyle.BALANCED);
        assertEquals(baseline.homeLambda(), styled.homeLambda(), 1e-9, "homeLambda");
        assertEquals(baseline.awayLambda(), styled.awayLambda(), 1e-9, "awayLambda");
        assertEquals(baseline.totalLambda(), styled.totalLambda(), 1e-9, "totalLambda");
        assertEquals(baseline.homeShare(), styled.homeShare(), 1e-9, "homeShare");
    }

    @Test
    void allStyleCombinationsStayWithinClamps() {
        TeamStyle[] styles = TeamStyle.values();
        int[][] ovrPairs = {{75, 75}, {80, 70}, {90, 60}, {60, 90}, {52, 48}};
        for (int[] ovr : ovrPairs) {
            for (TeamStyle hs : styles) {
                for (TeamStyle as : styles) {
                    var l = MatchQualityComputer.computeLambdas(ovr[0], ovr[1], hs, as);
                    assertTrue(l.totalLambda() >= 2.3 - 1e-9 && l.totalLambda() <= 3.05 + 1e-9,
                        "totalLambda=" + l.totalLambda() + " out of [2.3, 3.05] for " + hs + " vs " + as);
                    assertTrue(l.homeShare() >= 0.25 - 1e-9 && l.homeShare() <= 0.75 + 1e-9,
                        "homeShare=" + l.homeShare() + " out of [0.25, 0.75] for " + hs + " vs " + as);
                }
            }
        }
    }

    @Test
    void allStyleCombinationsAreFinite() {
        TeamStyle[] styles = TeamStyle.values();
        int[][] ovrPairs = {{75, 75}, {80, 70}, {90, 60}, {60, 90}, {52, 48}};
        for (int[] ovr : ovrPairs) {
            for (TeamStyle hs : styles) {
                for (TeamStyle as : styles) {
                    var l = MatchQualityComputer.computeLambdas(ovr[0], ovr[1], hs, as);
                    assertFalse(Double.isNaN(l.homeLambda()), "NaN homeLambda for " + hs + " vs " + as);
                    assertFalse(Double.isInfinite(l.homeLambda()), "Inf homeLambda for " + hs + " vs " + as);
                    assertFalse(Double.isNaN(l.awayLambda()), "NaN awayLambda for " + hs + " vs " + as);
                    assertFalse(Double.isInfinite(l.awayLambda()), "Inf awayLambda for " + hs + " vs " + as);
                    assertFalse(Double.isNaN(l.totalLambda()), "NaN totalLambda for " + hs + " vs " + as);
                    assertFalse(Double.isInfinite(l.totalLambda()), "Inf totalLambda for " + hs + " vs " + as);
                }
            }
        }
    }

    @Test
    void styleEffectsAreSmall() {
        TeamStyle[] styles = TeamStyle.values();
        int[][] ovrPairs = {{75, 75}, {80, 70}, {90, 60}};
        for (int[] ovr : ovrPairs) {
            var baseline = MatchQualityComputer.computeLambdas(ovr[0], ovr[1]);
            for (TeamStyle hs : styles) {
                for (TeamStyle as : styles) {
                    var styled = MatchQualityComputer.computeLambdas(ovr[0], ovr[1], hs, as);
                    double totalChange = Math.abs(styled.totalLambda() - baseline.totalLambda()) / baseline.totalLambda();
                    assertTrue(totalChange <= 0.10 + 1e-9,
                        "totalLambda change " + (totalChange * 100) + "% exceeds 10% for " + hs + " vs " + as);
                    double homeChange = Math.abs(styled.homeLambda() - baseline.homeLambda()) / Math.max(baseline.homeLambda(), 0.01);
                    assertTrue(homeChange <= 0.15 + 1e-9,
                        "homeLambda change " + (homeChange * 100) + "% exceeds 15% for " + hs + " vs " + as);
                    double awayChange = Math.abs(styled.awayLambda() - baseline.awayLambda()) / Math.max(baseline.awayLambda(), 0.01);
                    assertTrue(awayChange <= 0.15 + 1e-9,
                        "awayLambda change " + (awayChange * 100) + "% exceeds 15% for " + hs + " vs " + as);
                }
            }
        }
    }

    @Test
    void attackingVsDefensiveHasExpectedDirection() {
        // ATTACKING home vs BALANCED away: homeLambda should not decrease
        var attacking = MatchQualityComputer.computeLambdas(75, 75, TeamStyle.ATTACKING, TeamStyle.BALANCED);
        var balanced = MatchQualityComputer.computeLambdas(75, 75);
        assertTrue(attacking.homeLambda() >= balanced.homeLambda() - 1e-9,
            "ATTACKING homeLambda should not be less than BALANCED");
        assertTrue(attacking.totalLambda() >= balanced.totalLambda() - 1e-9,
            "ATTACKING totalLambda should not be less than BALANCED");

        // DEFENSIVE home vs BALANCED away: totalLambda should not increase
        var defensive = MatchQualityComputer.computeLambdas(75, 75, TeamStyle.DEFENSIVE, TeamStyle.BALANCED);
        assertTrue(defensive.totalLambda() <= balanced.totalLambda() + 1e-9,
            "DEFENSIVE totalLambda should not exceed BALANCED");
    }

    @Test
    void counterStyleDoesNotBreakUnderdogScenario() {
        // 70 vs 90 with home COUNTER: finite, clamped, both lambdas positive
        var l = MatchQualityComputer.computeLambdas(70, 90, TeamStyle.COUNTER, TeamStyle.BALANCED);
        assertFalse(Double.isNaN(l.homeLambda()));
        assertFalse(Double.isInfinite(l.homeLambda()));
        assertFalse(Double.isNaN(l.awayLambda()));
        assertFalse(Double.isInfinite(l.awayLambda()));
        assertTrue(l.totalLambda() >= 2.3 - 1e-9 && l.totalLambda() <= 3.05 + 1e-9);
        assertTrue(l.homeShare() >= 0.25 - 1e-9 && l.homeShare() <= 0.75 + 1e-9);
        assertTrue(l.homeLambda() > 0.0, "homeLambda should be positive for COUNTER underdog");
        assertTrue(l.awayLambda() > 0.0, "awayLambda should be positive");
    }
}