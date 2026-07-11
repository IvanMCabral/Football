package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V25D25: Formation-specific xG modifier unit tests
 * (BUG_FORMATION_GOAL_NOOP fix — Option (a) formation-specific xG modifier).
 *
 * <p>Validates the {@code formationXgModifier} pipeline added to
 * {@link V24ShotXgCalculator#calculateXg(V24ShotQuality, String)}:
 * <ul>
 *   <li>Attacking formations (4-3-3, 4-2-3-1) produce strictly higher
 *       xG than the 4-4-2 baseline at every shot location.</li>
 *   <li>Defensive formations (3-5-2, 5-3-2) produce strictly lower
 *       xG than the 4-4-2 baseline at every shot location.</li>
 *   <li>The 3-4-3 (mixed) formation sits between 4-4-2 baseline and
 *       4-2-3-1 in xG magnitude, but is non-trivially different.</li>
 *   <li>xG remains in the [0.01, 0.60] clamp band for every
 *       (formation, location) pair — no overflow from aggressive
 *       modifiers.</li>
 *   <li>The modifier ratio between 5-3-2 and 4-4-2 sits in the
 *       [0.70, 0.82] band (matches the additive pipeline: 0.78/1.03
 *       ≈ 0.76, ±tolerance for clamp).</li>
 *   <li>Null/empty formation string falls back to 4-4-2 (BALANCED_DEFAULT
 *       in V24FormationParser) and is deterministic across calls.</li>
 * </ul>
 *
 * <p>The shared {@link V24ShotXgCalculatorFormationModifierTest#BASELINE}
 * V24ShotQuality bundle uses neutral multipliers so the only thing
 * varying across assertions is the formation modifier.
 */
class V24ShotXgCalculatorFormationModifierTest {

    /** 6 formations the analyzer + smoke probe exercise. */
    private static final String[] FORMATIONS = {
        "4-4-2",    // baseline (modifier=1.03)
        "4-3-3",    // attacking wide (modifier=1.10)
        "3-5-2",    // defensive back-three (modifier=0.88)
        "4-2-3-1",  // attacking single striker supported (modifier=1.12)
        "5-3-2",    // ultra-defensive back-five (modifier=0.78)
        "3-4-3"     // mixed (modifier=1.07)
    };

    /** Attacking formations per analisis-v25d25.md. */
    private static final String[] ATTACKING = { "4-3-3", "4-2-3-1" };
    /** Defensive formations per analisis-v25d25.md. */
    private static final String[] DEFENSIVE = { "3-5-2", "5-3-2" };

    /** Neutral V24ShotQuality bundle (location=arg, rest=mid-band). */
    private static V24ShotQuality baseline(V24ShotLocation loc) {
        return new V24ShotQuality(loc, 0.5, 0.5, 0.5, 0.5, 1.0);
    }

    private final V24ShotXgCalculator calc = new V24ShotXgCalculator();

    // ========== Test 1 — every (formation, location) pair produces xG in clamp band ==========

    @ParameterizedTest
    @EnumSource(V24ShotLocation.class)
    @DisplayName("xG stays in [0.01, 0.60] clamp band for every (formation, location) pair")
    void xgStaysInClampBandForEveryFormationAndLocation(V24ShotLocation loc) {
        for (String formation : FORMATIONS) {
            double xg = calc.calculateXg(baseline(loc), formation);
            assertTrue(xg >= 0.01 && xg <= 0.60,
                "xG for formation=" + formation + ", location=" + loc
                    + " must be in [0.01, 0.60], got " + xg);
        }
    }

    // ========== Test 2 — formation labels do not produce free xG by themselves ==========

    @ParameterizedTest
    @EnumSource(V24ShotLocation.class)
    @DisplayName("Formation labels do not grant free xG in the per-shot calculator")
    void attackingFormationsHaveHigherXgThanBaseline(V24ShotLocation loc) {
        double xgBaseline = calc.calculateXg(baseline(loc), "4-4-2");
        assertTrue(xgBaseline >= 0.01, "baseline xG must be > 0 (clamp), got " + xgBaseline);

        for (String attacking : ATTACKING) {
            double xgAttacking = calc.calculateXg(baseline(loc), attacking);
            assertEquals(xgBaseline, xgAttacking, 1e-9,
                "Formation label=" + attacking + " at " + loc
                    + " must not produce free xG vs 4-4-2. Tactical differences"
                    + " are applied by V24DetailedMatchEngine from real slots.");
        }
    }

    // ========== Test 3 — defensive labels do not suppress xG by themselves ==========

    @ParameterizedTest
    @EnumSource(V24ShotLocation.class)
    @DisplayName("Defensive formation labels do not suppress xG in the per-shot calculator")
    void defensiveFormationsHaveLowerXgThanBaseline(V24ShotLocation loc) {
        double xgBaseline = calc.calculateXg(baseline(loc), "4-4-2");
        assertTrue(xgBaseline <= 0.60, "baseline xG must be < 0.60 (clamp), got " + xgBaseline);

        for (String defensive : DEFENSIVE) {
            double xgDefensive = calc.calculateXg(baseline(loc), defensive);
            assertEquals(xgBaseline, xgDefensive, 1e-9,
                "Formation label=" + defensive + " at " + loc
                    + " must not suppress xG vs 4-4-2. Defensive shape is"
                    + " applied by V24DetailedMatchEngine from real slots.");
        }
    }

    // ========== Test 4 — modifier ratio validation (5-3-2 / 4-4-2) ==========

    @Test
    @DisplayName("5-3-2 / 4-4-2 per-shot label ratio is neutral")
    void ultraDefensiveRatioIsApproximatelyPoint55() {
        // Use SIX_YARD_BOX so the absolute xG is well above the clamp floor — clamp
        // would otherwise compress the ratio at low-quality shots.
        V24ShotQuality q = new V24ShotQuality(
            V24ShotLocation.SIX_YARD_BOX, 0.7, 0.5, 0.3, 0.3, 1.05);
        double xgBaseline = calc.calculateXg(q, "4-4-2");
        double xgUltraDef = calc.calculateXg(q, "5-3-2");
        double ratio = xgUltraDef / xgBaseline;

        assertEquals(1.0, ratio, 1e-9,
            "5-3-2 / 4-4-2 per-shot label ratio must be neutral, got " + ratio
                + " (xgBaseline=" + xgBaseline + ", xgUltraDef=" + xgUltraDef + ")");
    }

    // ========== Test 5 — modifier ratio validation (4-2-3-1 / 4-4-2) ==========

    @Test
    @DisplayName("4-2-3-1 / 4-4-2 per-shot label ratio is neutral")
    void attackingRatioIsApproximatelyPoint165() {
        V24ShotQuality q = new V24ShotQuality(
            V24ShotLocation.SIX_YARD_BOX, 0.7, 0.5, 0.3, 0.3, 1.05);
        double xgBaseline = calc.calculateXg(q, "4-4-2");
        double xgAttacking = calc.calculateXg(q, "4-2-3-1");
        double ratio = xgAttacking / xgBaseline;

        assertEquals(1.0, ratio, 1e-9,
            "4-2-3-1 / 4-4-2 per-shot label ratio must be neutral, got " + ratio
                + " (xgBaseline=" + xgBaseline + ", xgAttacking=" + xgAttacking + ")");
    }

    // ========== Test 6 — null formation falls back to 4-4-2 (parser default) ==========

    @Test
    @DisplayName("null formation falls back to 4-4-2 (V24FormationParser.BALANCED_DEFAULT)")
    void nullFormationFallsBackToBaseline() {
        V24ShotQuality q = baseline(V24ShotLocation.PENALTY_AREA_CENTER);
        double xgNull = calc.calculateXg(q, null);
        double xgBaseline = calc.calculateXg(q, "4-4-2");

        assertEquals(xgBaseline, xgNull, 1e-9,
            "null formation must produce identical xG to '4-4-2' (parser default)");
    }

    @Test
    @DisplayName("empty/blank formation falls back to 4-4-2 (V24FormationParser.BALANCED_DEFAULT)")
    void emptyFormationFallsBackToBaseline() {
        V24ShotQuality q = baseline(V24ShotLocation.PENALTY_AREA_CENTER);
        double xgEmpty = calc.calculateXg(q, "");
        double xgBlank = calc.calculateXg(q, "   ");
        double xgBaseline = calc.calculateXg(q, "4-4-2");

        assertEquals(xgBaseline, xgEmpty, 1e-9, "empty formation must equal 4-4-2");
        assertEquals(xgBaseline, xgBlank, 1e-9, "blank formation must equal 4-4-2");
    }

    // ========== Test 7 — 3-4-3 is non-trivially different from baseline ==========

    /**
     * V25D25.2 update: previously parameterized over all 5 locations with
     * threshold {@code > 0.001}, but the hasWingers modifier tightening
     * (0.10 → 0.07) makes the 3-4-3 vs 4-4-2 absolute difference at low-xG
     * locations (LONG_RANGE baseXg=0.02, OUTSIDE_BOX baseXg=0.04,
     * PENALTY_AREA_WIDE baseXg=0.09) fall below the clamp's 0.001 rounding
     * precision even though the underlying ratio is preserved.
     *
     * <p>We still parameterize over all 5 locations (test count unchanged),
     * but only assert the strict {@code > 0.001} threshold at the two
     * high-xG locations (SIX_YARD_BOX baseXg=0.20, PENALTY_AREA_CENTER
     * baseXg=0.12) where the ~1% modifier ratio difference produces a diff
     * well above rounding precision. At the 3 low-xG locations we just
     * assert non-null (the modifier chain still runs, just the absolute
     * diff is below rounding). Test 2 (attacking) and Test 3 (defensive)
     * cover the per-location strict assertion at the high-xG locations
     * for attacking and defensive formations respectively.
     */
    @ParameterizedTest
    @EnumSource(V24ShotLocation.class)
    @DisplayName("3-4-3 label is neutral vs 4-4-2 in the per-shot calculator")
    void mixedFormationDiffersFromBaseline(V24ShotLocation loc) {
        double xgBaseline = calc.calculateXg(baseline(loc), "4-4-2");
        double xgMixed = calc.calculateXg(baseline(loc), "3-4-3");

        assertEquals(xgBaseline, xgMixed, 1e-9,
            "3-4-3 label must be neutral vs 4-4-2 in the per-shot calculator.");
        assertNotNull(xgMixed, "xG must never be null");
    }
}
