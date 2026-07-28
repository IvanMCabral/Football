package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * <ul>
 *   <li>TACKLER multiplica el xG por {@code (1 - skill/250)}. Modelo "avg
 *       defender skill" — el engine agrega TACKLER entre los DEF on-pitch
 *       del oponente y lo pasa como {@code defenderSkills} al overload
 *       11-args de {@link ShotXgCalculator}.</li>
 *   <li>Calibration:
 *     <ul>
 *       <li>TACKLER=0 → ×1.0 (no change)</li>
 *       <li>TACKLER=50 → ×0.80 (-20%)</li>
 *       <li>TACKLER=90 → ×0.64 (-36%)</li>
 *       <li>TACKLER=99 → ×0.604 (-39.6%)</li>
 *     </ul>
 *   </li>
 *   <li>TACKLER gating: aplica SOLO en {@link ShotEventType#OPEN_PLAY}.
 *     En CORNER / CROSS / PENALTY NO aplica — modelo "entradas en juego
 *     abierto", no en balon parado.</li>
 *   <li>Absent/null TACKLER skill → multiplier = 1.0 (no change).</li>
 *   <li>No-op regression: overload 10-args delega al 11-args con
 *       {@code Map.of()} (sin defender skills) → TACKLER no aplica, bit-a-bit
 * </ul>
 */
class ShotXgCalculatorTacklerTest {

    private static final ShotQuality BASELINE_QUALITY = new ShotQuality(
        ShotLocation.SIX_YARD_BOX, 0.7, 0.7, 0.3, 0.7, 1.0
    );

    private static double baselineXg(ShotXgCalculator calc, ShotEventType eventSubType) {
        return calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                eventSubType);
    }

    private static double xgWithTackler(ShotXgCalculator calc, int tacklerSkill,
                                        ShotEventType eventSubType) {
        Map<PlayerSkill, Integer> defenderSkills = new HashMap<>();
        if (tacklerSkill > 0) {
            defenderSkills.put(PlayerSkill.TACKLER, tacklerSkill);
        }
        return calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                eventSubType,
                defenderSkills, null);
    }

    // ========== TACKLER multiplier math (OPEN_PLAY) ==========

    @Test
    void tackler90_onOpenPlay_xgEqualsBaselineTimes0_64() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.OPEN_PLAY);

        double xg = xgWithTackler(calc, 90, ShotEventType.OPEN_PLAY);

        assertEquals(baseline * (1.0 - 90.0 / 250.0), xg, 0.001,
                "TACKLER=90 en OPEN_PLAY debe dar baseline × 0.64 (-36%)");
    }

    @Test
    void tackler50_onOpenPlay_xgEqualsBaselineTimes0_80() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.OPEN_PLAY);

        double xg = xgWithTackler(calc, 50, ShotEventType.OPEN_PLAY);

        assertEquals(baseline * (1.0 - 50.0 / 250.0), xg, 0.001,
                "TACKLER=50 en OPEN_PLAY debe dar baseline × 0.80 (-20%)");
    }

    @Test
    void tackler99_onOpenPlay_xgEqualsBaselineTimes0_604() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.OPEN_PLAY);

        double xg = xgWithTackler(calc, 99, ShotEventType.OPEN_PLAY);

        assertEquals(baseline * (1.0 - 99.0 / 250.0), xg, 0.001,
                "TACKLER=99 en OPEN_PLAY debe dar baseline × 0.604 (-39.6%)");
    }

    @Test
    void tackler0_onOpenPlay_xgEqualsBaseline() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.OPEN_PLAY);

        double xg = xgWithTackler(calc, 0, ShotEventType.OPEN_PLAY);

        assertEquals(baseline, xg, 0.001,
                "TACKLER=0 (o ausente) en OPEN_PLAY debe dar baseline");
    }

    // ========== TACKLER gating: NO aplica en CORNER/CROSS/PENALTY ==========

    @Test
    void tackler90_onCorner_doesNotApply() {
        // TACKLER gated por OPEN_PLAY — en CORNER no aplica (ahi manda WALL).
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.CORNER);

        double xg = xgWithTackler(calc, 90, ShotEventType.CORNER);

        assertEquals(baseline, xg, 0.001,
                "TACKLER=90 en CORNER debe preservar baseline (gated OPEN_PLAY only)");
    }

    @Test
    void tackler90_onCross_doesNotApply() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.CROSS);

        double xg = xgWithTackler(calc, 90, ShotEventType.CROSS);

        assertEquals(baseline, xg, 0.001,
                "TACKLER=90 en CROSS debe preservar baseline (gated OPEN_PLAY only)");
    }

    @Test
    void tackler90_onSetPieceTypes_doesNotApply() {
        // TACKLER gated por OPEN_PLAY — no aplica en CORNER ni CROSS.
        // (El engine actualmente no modela PENALTY como eventSubType separado
        // — un penal se trata como OPEN_PLAY por convencion del V24 model.
        // En el futuro si se agrega PENALTY, el gating debe revisarse.)
        ShotXgCalculator calc = new ShotXgCalculator();
        for (ShotEventType eventType : new ShotEventType[] {
                ShotEventType.CORNER, ShotEventType.CROSS }) {
            double baseline = baselineXg(calc, eventType);
            double xg = xgWithTackler(calc, 90, eventType);
            assertEquals(baseline, xg, 0.001,
                    "TACKLER=90 debe preservar baseline en eventType=" + eventType
                            + " (gated OPEN_PLAY only)");
        }
    }

    // ========== TACKLER + MARKER composition ==========

    @Test
    void tackler90_marker90_composeOnOpenPlay() {
        // Ambos aplican en OPEN_PLAY. MARKER=90 → 0.70. TACKLER=90 → 0.64.
        // Combined: 0.70 * 0.64 = 0.448 (-55.2%).
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.OPEN_PLAY);

        Map<PlayerSkill, Integer> defenderSkills = new HashMap<>();
        defenderSkills.put(PlayerSkill.MARKER, 90);
        defenderSkills.put(PlayerSkill.TACKLER, 90);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                ShotEventType.OPEN_PLAY,
                defenderSkills, null);

        double expected = baseline * 0.70 * 0.64;
        assertEquals(expected, xg, 0.001,
                "MARKER=90 + TACKLER=90 en OPEN_PLAY deben compound (0.70 * 0.64 = 0.448)");
    }

    @Test
    void tackler90_marker90_onCorner_markerAppliesTacklerDoesNot() {
        // En CORNER: TACKLER gated off (multiplier 1.0), MARKER aplica (0.70).
        // Combined: 1.0 * 0.70 = 0.70 (solo MARKER).
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.CORNER);

        Map<PlayerSkill, Integer> defenderSkills = new HashMap<>();
        defenderSkills.put(PlayerSkill.MARKER, 90);
        defenderSkills.put(PlayerSkill.TACKLER, 90);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                ShotEventType.CORNER,
                defenderSkills, null);

        double expected = baseline * 0.70;  // solo MARKER (TACKLER gated off en CORNER)
        assertEquals(expected, xg, 0.001,
                "En CORNER: TACKLER gated off, MARKER=90 aplica (solo 0.70)");
    }

    // ========== TACKLER + WALL composition ==========

    @Test
    void tackler90_composesWithWall99_onOpenPlay() {
        // TACKLER=90 → multiplier 0.64. WALL=99 → divisor 1.66.
        // Combined: 0.64 / 1.66 = 0.3855 (-61.4%).
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.OPEN_PLAY);

        Map<PlayerSkill, Integer> gkSkills = new HashMap<>();
        gkSkills.put(PlayerSkill.WALL, 99);
        Map<PlayerSkill, Integer> defenderSkills = new HashMap<>();
        defenderSkills.put(PlayerSkill.TACKLER, 90);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                gkSkills, null,
                ShotEventType.OPEN_PLAY,
                defenderSkills, null);

        double expected = baseline * 0.64 / 1.66;
        assertEquals(expected, xg, 0.001,
                "TACKLER=90 + WALL=99 en OPEN_PLAY deben componerse (0.64 / 1.66)");
    }

    // ========== No-op regression ==========

    @Test
    void overload10Args_preservesV25D32Baseline() {
        ShotXgCalculator calc = new ShotXgCalculator();

        double xg5 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0);
        double xg10 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                ShotEventType.OPEN_PLAY);

        assertEquals(xg5, xg10, 0.0001,
                "10-args debe preservar V25D32 baseline (TACKLER no aplica via 10-args)");
    }

    @Test
    void absentTacklerSkill_inDefenderSkillsMap_doesNotApply() {
        // Edge case: defenderSkills map no contiene TACKLER key. No debe
        // tirar NPE, debe preservar baseline.
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.OPEN_PLAY);

        // Solo MARKER en defender skills — TACKLER ausente.
        Map<PlayerSkill, Integer> onlyMarker = new HashMap<>();
        onlyMarker.put(PlayerSkill.MARKER, 90);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                ShotEventType.OPEN_PLAY,
                onlyMarker, null);

        // TACKLER ausente → tacklerMult = 1.0. Solo MARKER=90 aplica → 0.70.
        double expected = baseline * 0.70;
        assertEquals(expected, xg, 0.001,
                "TACKLER ausente en defenderSkills + MARKER=90 → solo MARKER aplica");
    }

    @Test
    void nullDefenderSkills_doesNotApply() {
        // Edge case: defenderSkills=null. No debe tirar NPE.
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc, ShotEventType.OPEN_PLAY);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                ShotEventType.OPEN_PLAY,
                null, null);

        assertEquals(baseline, xg, 0.001,
                "defenderSkills=null debe preservar baseline (no NPE)");
    }

    // ========== Comportamiento bajo clamp ==========

    @Test
    void tacklerMax_withLowBaseXg_isClampedToMinXg() {
        // Sanity: TACKLER=99 + base muy bajo → puede llegar al floor de 0.01.
        ShotXgCalculator calc = new ShotXgCalculator();

        ShotQuality lowBaseQ = new ShotQuality(
                ShotLocation.LONG_RANGE, 0.1, 0.1, 0.9, 0.9, 0.5);

        double xg = calc.calculateXg(lowBaseQ, "4-3-3", "4-4-2",
                50.0, 90.0,
                Map.of(), null,
                Map.of(), null,
                ShotEventType.OPEN_PLAY,
                Map.of(PlayerSkill.TACKLER, 99), null);

        assertTrue(xg >= 0.01, "xG con TACKLER=99 + base bajo debe clampear a 0.01 min");
        assertTrue(xg <= 0.60, "xG <= 0.60 max");
    }
}