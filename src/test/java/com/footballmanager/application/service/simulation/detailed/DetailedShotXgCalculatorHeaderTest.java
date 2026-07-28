package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ShotXgCalculator#calculateXg} overload 10-args.
 *
 * <ul>
 *   <li>HEADER multiplier = {@code 1.0 + (skill / 200.0)}</li>
 *   <li>HEADER=0 → ×1.0 (no change)</li>
 *   <li>HEADER=80 → ×1.40 (+40%)</li>
 *   <li>HEADER=99 → ×1.495 (+49.5%)</li>
 *   <li>Multiplier applies ONLY on {@link ShotEventType#CORNER} or
 *       {@link ShotEventType#CROSS} — never on {@link ShotEventType#OPEN_PLAY}.</li>
 *   <li>Absent/null HEADER skill → treated as 0 (no change).</li>
 * </ul>
 *
 * <p>Regression checks:
 * <ul>
 *   <li>9-args overload (with skills passed) + OPEN_PLAY implicit default
 *   <li>10-args with OPEN_PLAY + non-zero HEADER → identical to 5-args baseline
 *       (HEADER gating works).</li>
 * </ul>
 */
class ShotXgCalculatorHeaderTest {

    private static final ShotQuality BASELINE_QUALITY = new ShotQuality(
        ShotLocation.SIX_YARD_BOX, 0.7, 0.7, 0.3, 0.7, 1.0
    );

    /** Baseline xG sin HEADER multiplier (sin skills, eventSubType irrelevante). */
    private static double baselineXg(ShotXgCalculator calc) {
        return calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0);
    }

    private static Map<PlayerSkill, Integer> skillsWithHeader(int level) {
        Map<PlayerSkill, Integer> skills = new HashMap<>();
        if (level > 0) {
            skills.put(PlayerSkill.HEADER, level);
        }
        return skills;
    }

    // ========== HEADER multiplier math (CORNER + CROSS) ==========

    @Test
    void header99_onCorner_xgEqualsBaselineTimes1_495() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xgWithHeader = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeader(99), null,
                Map.of(), null,
                ShotEventType.CORNER);

        double expected = baseline * (1.0 + 99.0 / 200.0);
        assertEquals(expected, xgWithHeader, 0.001,
                "HEADER=99 en CORNER debe dar baseline × 1.495");
    }

    @Test
    void header80_onCorner_xgEqualsBaselineTimes1_40() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xgWithHeader = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeader(80), null,
                Map.of(), null,
                ShotEventType.CORNER);

        double expected = baseline * (1.0 + 80.0 / 200.0);
        assertEquals(expected, xgWithHeader, 0.001,
                "HEADER=80 en CORNER debe dar baseline × 1.40");
    }

    @Test
    void header0_onCorner_xgEqualsBaseline() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xgNoHeader = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeader(0), null,
                Map.of(), null,
                ShotEventType.CORNER);

        assertEquals(baseline, xgNoHeader, 0.001,
                "HEADER=0 (o ausente) en CORNER debe dar baseline sin cambio");
    }

    @Test
    void absentHeaderSkill_onCorner_xgEqualsBaseline() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        // skills map vacio (sin HEADER key)
        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                ShotEventType.CORNER);

        assertEquals(baseline, xg, 0.001,
                "Sin HEADER skill (map vacio) en CORNER debe dar baseline");
    }

    @Test
    void nullShooterSkills_onCorner_xgEqualsBaseline() {
        // Edge case: shooterSkills=null. El engine pasa null cuando PlayerMatchState
        // tiene skillLevels vacio. No debe tirar NPE.
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                null, null,
                Map.of(), null,
                ShotEventType.CORNER);

        assertEquals(baseline, xg, 0.001,
                "shooterSkills=null en CORNER debe dar baseline (no NPE)");
    }

    @Test
    void header80_onCross_xgEqualsBaselineTimes1_40() {
        // CROSS comparte la misma logica que CORNER (ambos disparan HEADER multiplier).
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeader(80), null,
                Map.of(), null,
                ShotEventType.CROSS);

        double expected = baseline * (1.0 + 80.0 / 200.0);
        assertEquals(expected, xg, 0.001,
                "HEADER=80 en CROSS debe dar baseline × 1.40 (mismo que CORNER)");
    }

    // ========== HEADER gating (OPEN_PLAY no aplica) ==========

    @Test
    void header80_onOpenPlay_xgEqualsBaseline() {
        // Regression: HEADER NO debe aplicar en OPEN_PLAY. Esta es la garantia
        // implicitamente OPEN_PLAY y deben quedar identicos.
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeader(80), null,
                Map.of(), null,
                ShotEventType.OPEN_PLAY);

        assertEquals(baseline, xg, 0.001,
                "HEADER=80 en OPEN_PLAY debe dar baseline (sin multiplier)");
    }

    @Test
    void header99_onOpenPlay_xgEqualsBaseline() {
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeader(99), null,
                Map.of(), null,
                ShotEventType.OPEN_PLAY);

        assertEquals(baseline, xg, 0.001,
                "HEADER=99 en OPEN_PLAY debe dar baseline (sin multiplier)");
    }

    @Test
    void overload9ArgsWithHeaderSkillAndOpenPlay_preservesV25D32Baseline() {
        // dar el MISMO resultado que el 5-args (sin skills) — porque el
        // 9-args delega al 10-args con OPEN_PLAY default (HEADER gated off).
        ShotXgCalculator calc = new ShotXgCalculator();

        double xg5 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0);
        double xg9 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                skillsWithHeader(99), null,
                Map.of(), null);

        assertEquals(xg5, xg9, 0.0001,
                "9-args con HEADER=99 + OPEN_PLAY default debe preservar V25D32 baseline");
    }

    @Test
    void overload9ArgsWithCornerAndNoHeader_preservesV25D32Baseline() {
        // que el branch CORNER esta bien gateado (no afecta si no hay skill).
        ShotXgCalculator calc = new ShotXgCalculator();

        double xg5 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0);
        double xg10 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                ShotEventType.CORNER);

        assertEquals(xg5, xg10, 0.0001,
                "10-args con CORNER pero sin HEADER skill debe preservar baseline");
    }

    // ========== Comportamiento bajo clamp ==========

    @Test
    void headerMax_withHighBaseXg_isClampedToMaxXg() {
        // Verifica que el HEADER multiplier no rompe el clamp [0.01, 0.60].
        // Tomamos SIX_YARD_BOX (highest baseXg) + HEADER=99 → esperamos
        // clamp a 0.60 si supera el techo.
        ShotXgCalculator calc = new ShotXgCalculator();

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeader(99), null,
                Map.of(), null,
                ShotEventType.CORNER);

        assertTrue(xg <= 0.60, "xG con HEADER=99 en SIX_YARD_BOX debe clampear a 0.60 max");
        assertTrue(xg >= 0.01, "xG siempre >= 0.01 (clamp floor)");
    }

    @Test
    void headerMultiplier_appliesOnTopOfFormationOffensiveModifier() {
        // Verifica que HEADER multiplica DESPUES del formationOffensiveModifier
        // (no se aplica antes — el orden importa porque HEADER es un bonus
        // adicional al shot, no un cambio del "tipo" de formation).
        // 4-3-3 vs 4-4-2 baseline (mod 1.40) con HEADER=50 → ×1.25
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeader(50), null,
                Map.of(), null,
                ShotEventType.CORNER);

        double expected = baseline * (1.0 + 50.0 / 200.0);  // 1.25
        assertEquals(expected, xg, 0.001,
                "HEADER=50 en CORNER debe multiplicar sobre el xG ya formation-modulado");
    }

    // ========== Combinations: HEADER + otras skills ==========

    @Test
    void multipleSkillsOnlyHeaderAppliesOnCorner() {
        // SHOOTER/DRIBBLER/SPEEDSTER presentes pero no implementados en F1
        // skills deben ser ignorados por ahora.
        ShotXgCalculator calc = new ShotXgCalculator();
        double baseline = baselineXg(calc);

        Map<PlayerSkill, Integer> manySkills = new HashMap<>();
        manySkills.put(PlayerSkill.HEADER, 80);
        manySkills.put(PlayerSkill.SHOOTER, 99);
        manySkills.put(PlayerSkill.DRIBBLER, 95);
        manySkills.put(PlayerSkill.SPEEDSTER, 88);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                manySkills, null,
                Map.of(), null,
                ShotEventType.CORNER);

        double expected = baseline * (1.0 + 80.0 / 200.0);
        assertEquals(expected, xg, 0.001,
                "Solo HEADER debe aplicar en F1; SHOOTER/DRIBBLER/SPEEDSTER ignorados");
    }
}