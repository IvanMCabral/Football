package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D34-F2: MARKER skill impact on xG (1v1 marcaje reduction).
 *
 * <p>Spec (V25D34 prompt, F2):
 * <ul>
 *   <li>MARKER multiplica el xG por {@code (1 - skill/300)}. Modelo "avg
 *       defender skill" — el engine agrega MARKER entre los DEF on-pitch del
 *       oponente y lo pasa como {@code defenderSkills} al overload 11-args
 *       de {@link V24ShotXgCalculator}.</li>
 *   <li>Calibration:
 *     <ul>
 *       <li>MARKER=0 → ×1.0 (no change)</li>
 *       <li>MARKER=50 → ×0.833 (-16.7%)</li>
 *       <li>MARKER=90 → ×0.70 (-30%)</li>
 *       <li>MARKER=99 → ×0.67 (-33%)</li>
 *     </ul>
 *   </li>
 *   <li>MARKER gating:
 *     <ul>
 *       <li>Aplica SIEMPRE (en cualquier {@link V24ShotEventType}) porque
 *           los duelos 1v1 ocurren en cualquier contexto — corner, cross,
 *           open play.</li>
 *       <li>Absent/null MARKER skill → multiplier = 1.0 (no change).</li>
 *     </ul>
 *   </li>
 *   <li>No-op regression: overload 10-args delega al 11-args con
 *       {@code Map.of()} (sin defender skills) → MARKER no aplica, bit-a-bit
 *       identico a V25D33.</li>
 * </ul>
 */
class V24ShotXgCalculatorMarkerTest {

    private static final V24ShotQuality BASELINE_QUALITY = new V24ShotQuality(
        V24ShotLocation.SIX_YARD_BOX, 0.7, 0.7, 0.3, 0.7, 1.0
    );

    private static double baselineXg(V24ShotXgCalculator calc) {
        // Baseline = overload 10-args sin defending skills (sin MARKER).
        return calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY);
    }

    private static double xgWithMarker(V24ShotXgCalculator calc, int markerSkill,
                                       V24ShotEventType eventSubType) {
        Map<PlayerSkill, Integer> defenderSkills = new HashMap<>();
        if (markerSkill > 0) {
            defenderSkills.put(PlayerSkill.MARKER, markerSkill);
        }
        return calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                eventSubType,
                defenderSkills, null);
    }

    // ========== MARKER multiplier math ==========

    @Test
    void marker90_xgEqualsBaselineTimes0_70() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = xgWithMarker(calc, 90, V24ShotEventType.OPEN_PLAY);

        assertEquals(baseline * (1.0 - 90.0 / 300.0), xg, 0.001,
                "MARKER=90 debe dar baseline × 0.70 (-30%)");
    }

    @Test
    void marker50_xgEqualsBaselineTimes0_833() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = xgWithMarker(calc, 50, V24ShotEventType.OPEN_PLAY);

        assertEquals(baseline * (1.0 - 50.0 / 300.0), xg, 0.001,
                "MARKER=50 debe dar baseline × 0.833 (-16.7%)");
    }

    @Test
    void marker99_xgEqualsBaselineTimes0_67() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = xgWithMarker(calc, 99, V24ShotEventType.OPEN_PLAY);

        assertEquals(baseline * (1.0 - 99.0 / 300.0), xg, 0.001,
                "MARKER=99 debe dar baseline × 0.67 (-33%)");
    }

    @Test
    void marker0_xgEqualsBaseline() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = xgWithMarker(calc, 0, V24ShotEventType.OPEN_PLAY);

        assertEquals(baseline, xg, 0.001,
                "MARKER=0 (o ausente) debe dar baseline sin cambio");
    }

    // ========== MARKER gating: aplica SIEMPRE (no eventSubType gate) ==========

    @Test
    void marker90_onOpenPlay_applies() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = xgWithMarker(calc, 90, V24ShotEventType.OPEN_PLAY);

        assertEquals(baseline * 0.70, xg, 0.001,
                "MARKER=90 en OPEN_PLAY debe aplicar (-30%)");
    }

    @Test
    void marker90_onCorner_applies() {
        // MARKER no esta gated por eventSubType — aplica en corner tambien
        // (los duelos 1v1 ocurren en cualquier contexto).
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        // Baseline sin skills (corner sin HEADER): usa overload 10-args sin
        // defender skills.
        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.CORNER);

        double xg = xgWithMarker(calc, 90, V24ShotEventType.CORNER);

        assertEquals(baseline * 0.70, xg, 0.001,
                "MARKER=90 en CORNER debe aplicar (-30%, sin gating)");
    }

    @Test
    void marker90_onCross_applies() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.CROSS);

        double xg = xgWithMarker(calc, 90, V24ShotEventType.CROSS);

        assertEquals(baseline * 0.70, xg, 0.001,
                "MARKER=90 en CROSS debe aplicar (-30%, sin gating)");
    }

    @Test
    void marker90_onAllEventTypes_applies() {
        // MARKER no esta gated por eventSubType — aplica en cualquier
        // contexto donde haya duel. Verificacion rapida sobre los 3 tipos
        // disponibles en V24ShotEventType.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        for (V24ShotEventType eventType : V24ShotEventType.values()) {
            double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                    70.0, 70.0,
                    Map.of(), null,
                    Map.of(), null,
                    eventType);
            double xg = xgWithMarker(calc, 90, eventType);
            assertEquals(baseline * 0.70, xg, 0.001,
                    "MARKER=90 debe aplicar en eventType=" + eventType + " (sin gating)");
        }
    }

    // ========== MARKER + HEADER composition ==========

    @Test
    void marker90_composesWithHeader80_onCorner() {
        // En CORNER, HEADER multiplica 1.40 y MARKER multiplica 0.70 — ambos
        // aplican (MARKER no esta gated). Compounding: 1.40 * 0.70 = 0.98
        // (-2% sobre baseline-HEADER).
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        Map<PlayerSkill, Integer> shooterSkills = new HashMap<>();
        shooterSkills.put(PlayerSkill.HEADER, 80);
        Map<PlayerSkill, Integer> defenderSkills = new HashMap<>();
        defenderSkills.put(PlayerSkill.MARKER, 90);

        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.CORNER);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                shooterSkills, null,
                Map.of(), null,
                V24ShotEventType.CORNER,
                defenderSkills, null);

        // baseline-HEADER = baseline * 1.40; xg = baseline-HEADER * 0.70
        double expected = baseline * 1.40 * 0.70;
        assertEquals(expected, xg, 0.001,
                "MARKER=90 + HEADER=80 en CORNER deben compound (1.40 * 0.70 = 0.98)");
    }

    @Test
    void marker90_composesWithWall99_onOpenPlay() {
        // MARKER (multiplier) + WALL (divisor) ambos aplican en OPEN_PLAY.
        // WALL=99 → divisor = 1.66 → xg / 1.66. MARKER=90 → multiplier 0.70.
        // Combined effect: xg * 0.70 / 1.66 = xg * 0.4217 (≈ -58%).
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        Map<PlayerSkill, Integer> gkSkills = new HashMap<>();
        gkSkills.put(PlayerSkill.WALL, 99);
        Map<PlayerSkill, Integer> defenderSkills = new HashMap<>();
        defenderSkills.put(PlayerSkill.MARKER, 90);

        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                gkSkills, null,
                V24ShotEventType.OPEN_PLAY,
                defenderSkills, null);

        double expected = baseline * 0.70 / 1.66;
        assertEquals(expected, xg, 0.001,
                "MARKER=90 + WALL=99 en OPEN_PLAY deben componerse (0.70 / 1.66)");
    }

    // ========== No-op regression ==========

    @Test
    void overload10ArgsWithEmptyDefenderSkills_preservesV25D32Baseline() {
        // V25D33 plumbing test: overload 10-args con shooterSkills vacios +
        // WALL absent → bit-a-bit identico al 5-args (V25D32 baseline).
        // MARKER no aparece porque el 10-args delega al 11-args con Map.of().
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double xg5 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0);
        double xg10 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY);

        assertEquals(xg5, xg10, 0.0001,
                "10-args con defender skills vacios debe preservar V25D32 baseline");
    }

    @Test
    void absentMarkerSkill_inDefenderSkillsMap_doesNotApply() {
        // Edge case: defenderSkills map no contiene MARKER key. No debe
        // tirar NPE, debe preservar baseline.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY,
                Map.of(), null);

        assertEquals(baseline, xg, 0.001,
                "MARKER ausente en defenderSkills debe preservar baseline");
    }

    @Test
    void nullDefenderSkills_doesNotApply() {
        // Edge case: defenderSkills=null. No debe tirar NPE.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY,
                null, null);

        assertEquals(baseline, xg, 0.001,
                "defenderSkills=null debe preservar baseline (no NPE)");
    }

    @Test
    void onlyTacklerInDefenderSkills_doesNotApplyMarker() {
        // Sanity: si defenderSkills solo tiene TACKLER (no MARKER), el
        // MARKER multiplier sigue siendo 1.0 (no-op). TACKLER gating se
        // cubre en V24ShotXgCalculatorTacklerTest.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.CORNER);  // CORNER: TACKLER gated off

        Map<PlayerSkill, Integer> onlyTackler = new HashMap<>();
        onlyTackler.put(PlayerSkill.TACKLER, 90);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.CORNER,
                onlyTackler, null);

        assertEquals(baseline, xg, 0.001,
                "Solo TACKLER en defenderSkills + CORNER → baseline (MARKER+TACKLER no aplican)");
    }

    // ========== Comportamiento bajo clamp ==========

    @Test
    void markerMax_withLowBaseXg_isClampedToMinXg() {
        // Sanity: MARKER=99 + base muy bajo → puede llegar al floor de 0.01.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        V24ShotQuality lowBaseQ = new V24ShotQuality(
                V24ShotLocation.LONG_RANGE, 0.1, 0.1, 0.9, 0.9, 0.5);

        double xg = calc.calculateXg(lowBaseQ, "4-3-3", "4-4-2",
                50.0, 90.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY,
                Map.of(PlayerSkill.MARKER, 99), null);

        assertTrue(xg >= 0.01, "xG con MARKER=99 + base bajo debe clampear a 0.01 min");
        assertTrue(xg <= 0.60, "xG <= 0.60 max");
    }
}