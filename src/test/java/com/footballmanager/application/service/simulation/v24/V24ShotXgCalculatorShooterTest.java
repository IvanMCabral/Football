package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D34-F1: SHOOTER skill impact on xG (long-range bonus).
 *
 * <p>Spec (V25D34 prompt, F1):
 * <ul>
 *   <li>SHOOTER multiplica el xG SOLO en {@link V24ShotLocation#LONG_RANGE}
 *       shots. Formula: {@code shooterLongRangeMult = 1 + skill/250}.</li>
 *   <li>Calibration:
 *     <ul>
 *       <li>SHOOTER=0 → ×1.0 (no change)</li>
 *       <li>SHOOTER=90 (Mbappé) → ×1.36 (+36%)</li>
 *       <li>SHOOTER=99 → ×1.396 (+39.6%)</li>
 *     </ul>
 *   </li>
 *   <li>SHOOTER gating:
 *     <ul>
 *       <li>Aplica SOLO en LONG_RANGE (location == V24ShotLocation.LONG_RANGE).</li>
 *       <li>El resto de las locations (SIX_YARD_BOX, PENALTY_AREA_*,
 *           OUTSIDE_BOX) NO reciben bonus aunque tengan SHOOTER alto.</li>
 *       <li>Absent/null SHOOTER skill → tratado como 0 (multiplier 1.0).</li>
 *     </ul>
 *   </li>
 *   <li>No-op regression: SHOOTER absent o location != LONG_RANGE → xG
 *       sin cambio, bit-a-bit identico a V25D33.</li>
 * </ul>
 */
class V24ShotXgCalculatorShooterTest {

    private static double baselineXgAt(V24ShotXgCalculator calc, V24ShotLocation loc) {
        V24ShotQuality q = new V24ShotQuality(loc, 0.7, 0.7, 0.3, 0.7, 1.0);
        return calc.calculateXg(q, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY);
    }

    private static Map<PlayerSkill, Integer> skillsWithShooter(int level) {
        Map<PlayerSkill, Integer> skills = new HashMap<>();
        if (level > 0) {
            skills.put(PlayerSkill.SHOOTER, level);
        }
        return skills;
    }

    private static double xgAt(V24ShotXgCalculator calc, V24ShotLocation loc, int shooterSkill) {
        V24ShotQuality q = new V24ShotQuality(loc, 0.7, 0.7, 0.3, 0.7, 1.0);
        return calc.calculateXg(q, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithShooter(shooterSkill), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY);
    }

    // ========== SHOOTER multiplier math (LONG_RANGE only) ==========

    @Test
    void shooter90_onLongRange_xgEqualsBaselineTimes1_36() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.LONG_RANGE);

        double xg = xgAt(calc, V24ShotLocation.LONG_RANGE, 90);

        double expected = baseline * (1.0 + 90.0 / 250.0);  // 1.36
        assertEquals(expected, xg, 0.001,
                "SHOOTER=90 en LONG_RANGE debe dar baseline × 1.36 (+36%)");
    }

    @Test
    void shooter99_onLongRange_xgEqualsBaselineTimes1_396() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.LONG_RANGE);

        double xg = xgAt(calc, V24ShotLocation.LONG_RANGE, 99);

        double expected = baseline * (1.0 + 99.0 / 250.0);  // 1.396
        assertEquals(expected, xg, 0.001,
                "SHOOTER=99 en LONG_RANGE debe dar baseline × 1.396 (+39.6%)");
    }

    @Test
    void shooter0_onLongRange_xgEqualsBaseline() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.LONG_RANGE);

        double xg = xgAt(calc, V24ShotLocation.LONG_RANGE, 0);

        assertEquals(baseline, xg, 0.001,
                "SHOOTER=0 (o ausente) en LONG_RANGE debe dar baseline sin cambio");
    }

    // ========== SHOOTER gating (location != LONG_RANGE no aplica) ==========

    @Test
    void shooter90_onSixYardBox_doesNotApply() {
        // El rematador de SIX_YARD_BOX (tiro a 6 metros, casi siempre gol
        // si llega) no depende de SHOOTER — ahi manda técnica / posición.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.SIX_YARD_BOX);

        double xg = xgAt(calc, V24ShotLocation.SIX_YARD_BOX, 90);

        assertEquals(baseline, xg, 0.001,
                "SHOOTER=90 en SIX_YARD_BOX debe preservar baseline (gated LONG_RANGE only)");
    }

    @Test
    void shooter90_onPenaltyAreaCenter_doesNotApply() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.PENALTY_AREA_CENTER);

        double xg = xgAt(calc, V24ShotLocation.PENALTY_AREA_CENTER, 90);

        assertEquals(baseline, xg, 0.001,
                "SHOOTER=90 en PENALTY_AREA_CENTER debe preservar baseline");
    }

    @Test
    void shooter90_onPenaltyAreaWide_doesNotApply() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.PENALTY_AREA_WIDE);

        double xg = xgAt(calc, V24ShotLocation.PENALTY_AREA_WIDE, 90);

        assertEquals(baseline, xg, 0.001,
                "SHOOTER=90 en PENALTY_AREA_WIDE debe preservar baseline");
    }

    @Test
    void shooter90_onOutsideBox_doesNotApply() {
        // OUTSIDE_BOX NO es LONG_RANGE (estan separados en el enum).
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.OUTSIDE_BOX);

        double xg = xgAt(calc, V24ShotLocation.OUTSIDE_BOX, 90);

        assertEquals(baseline, xg, 0.001,
                "SHOOTER=90 en OUTSIDE_BOX debe preservar baseline (OUTSIDE_BOX != LONG_RANGE)");
    }

    // ========== SHOOTER no-op (skill absent o 0) ==========

    @Test
    void absentShooterSkill_onLongRange_xgEqualsBaseline() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.LONG_RANGE);

        V24ShotQuality q = new V24ShotQuality(V24ShotLocation.LONG_RANGE, 0.7, 0.7, 0.3, 0.7, 1.0);
        double xg = calc.calculateXg(q, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY);

        assertEquals(baseline, xg, 0.001,
                "SHOOTER ausente en LONG_RANGE debe dar baseline sin cambio");
    }

    @Test
    void nullShooterSkills_onLongRange_xgEqualsBaseline() {
        // Edge case: shooterSkills=null. No debe tirar NPE.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.LONG_RANGE);

        V24ShotQuality q = new V24ShotQuality(V24ShotLocation.LONG_RANGE, 0.7, 0.7, 0.3, 0.7, 1.0);
        double xg = calc.calculateXg(q, "4-3-3", "4-4-2",
                70.0, 70.0,
                null, null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY);

        assertEquals(baseline, xg, 0.001,
                "shooterSkills=null en LONG_RANGE debe dar baseline (no NPE)");
    }

    // ========== SHOOTER + HEADER composition ==========

    @Test
    void shooter90_header80_composeOnCorner() {
        // SHOOTER + HEADER en CORNER: ambos aplican (HEADER gated CORNER, SHOOTER gated LONG_RANGE).
        // En este test el location es LONG_RANGE y eventSubType es CORNER — un corner
        // que termina en un long-range shot es raro, pero el codigo compone ambos
        // multipliers porque HEADER gating es por eventSubType y SHOOTER por location.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgAt(calc, V24ShotLocation.LONG_RANGE);

        Map<PlayerSkill, Integer> skills = new HashMap<>();
        skills.put(PlayerSkill.SHOOTER, 90);
        skills.put(PlayerSkill.HEADER, 80);

        V24ShotQuality q = new V24ShotQuality(V24ShotLocation.LONG_RANGE, 0.7, 0.7, 0.3, 0.7, 1.0);
        double xg = calc.calculateXg(q, "4-3-3", "4-4-2",
                70.0, 70.0,
                skills, null,
                Map.of(), null,
                V24ShotEventType.CORNER);

        // HEADER=80 → headerMult = 1.40. SHOOTER=90 → shooterLongRangeMult = 1.36.
        // Combined: 1.40 * 1.36 = 1.904
        double expected = baseline * 1.40 * 1.36;
        assertEquals(expected, xg, 0.001,
                "SHOOTER=90 + HEADER=80 en LONG_RANGE/CORNER deben compound");
    }

    @Test
    void shooterMax_withClamp_isBoundedToMaxXg() {
        // Sanity: SHOOTER max + LONG_RANGE no rompe el clamp [0.01, 0.60].
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double xg = xgAt(calc, V24ShotLocation.LONG_RANGE, 99);

        assertTrue(xg <= 0.60, "xG con SHOOTER=99 en LONG_RANGE debe clampear a 0.60 max");
        assertTrue(xg >= 0.01, "xG siempre >= 0.01 (clamp floor)");
    }

    // ========== Regression: 9-args preserva V25D32 cuando SHOOTER no aplica ==========

    @Test
    void overload9ArgsWithShooter_outsideLongRange_preservesV25D32Baseline() {
        // SHOOTER=99 + PENALTY_AREA_CENTER → SHOOTER gated off → bit-a-bit
        // identico al 5-args (V25D32 baseline).
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        V24ShotQuality q = new V24ShotQuality(V24ShotLocation.PENALTY_AREA_CENTER, 0.7, 0.7, 0.3, 0.7, 1.0);
        double xg5 = calc.calculateXg(q, "4-3-3", "4-4-2", 70.0, 70.0);
        double xg9 = calc.calculateXg(q, "4-3-3", "4-4-2", 70.0, 70.0,
                skillsWithShooter(99), null,
                Map.of(), null);

        assertEquals(xg5, xg9, 0.0001,
                "9-args con SHOOTER=99 en PENALTY_AREA_CENTER debe preservar V25D32 baseline");
    }
}