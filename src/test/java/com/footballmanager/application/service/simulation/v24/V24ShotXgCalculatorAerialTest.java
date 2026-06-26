package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D34-F1: AERIAL skill impact on HEADER multiplier (compounding).
 *
 * <p>Spec (V25D34 prompt, F1):
 * <ul>
 *   <li>AERIAL multiplica el HEADER multiplier cuando shooter height
 *       &ge; 185 cm. Formula: {@code headerMult *= (1 + aerialSkill/300)}.</li>
 *   <li>Compounding con HEADER (multiplicativo, no aditivo):
 *     <ul>
 *       <li>HEADER=0, AERIAL=80, height=190 → headerMult = 1.0 × 1.267 = 1.267</li>
 *       <li>HEADER=80, AERIAL=80, height=190 → headerMult = 1.4 × 1.267 = 1.774 (+77%)</li>
 *       <li>HEADER=99, AERIAL=99, height=190 → headerMult = 1.495 × 1.33 = 1.989 (+99%)</li>
 *     </ul>
 *   </li>
 *   <li>AERIAL gating:
 *     <ul>
 *       <li>Aplica SOLO en CORNER o CROSS (mismo gating que HEADER).</li>
 *       <li>Aplica SOLO si shooterHeightCm &ge; 185 (altura minima para
 *           "dominio aereo"). Height null o &lt; 185 → no compounding.</li>
 *       <li>AERIAL=0 o ausente → no compounding (headerMult unchanged).</li>
 *     </ul>
 *   </li>
 *   <li>No-op regression: AERIAL absent o height &lt; 185 → headerMult sin
 *       cambio, bit-a-bit identico a V25D33.</li>
 * </ul>
 */
class V24ShotXgCalculatorAerialTest {

    private static final V24ShotQuality BASELINE_QUALITY = new V24ShotQuality(
        V24ShotLocation.SIX_YARD_BOX, 0.7, 0.7, 0.3, 0.7, 1.0
    );

    private static double baselineXgWithHeader(V24ShotXgCalculator calc) {
        // Baseline = solo HEADER (sin AERIAL), sobre el baseline del V25D33.
        Map<PlayerSkill, Integer> onlyHeader = new HashMap<>();
        onlyHeader.put(PlayerSkill.HEADER, 80);
        return calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                onlyHeader, null,
                Map.of(), null,
                V24ShotEventType.CORNER);
    }

    private static Map<PlayerSkill, Integer> skillsWithHeaderAndAerial(int header, int aerial) {
        Map<PlayerSkill, Integer> skills = new HashMap<>();
        if (header > 0) skills.put(PlayerSkill.HEADER, header);
        if (aerial > 0) skills.put(PlayerSkill.AERIAL, aerial);
        return skills;
    }

    // ========== AERIAL compounding math (CORNER + CROSS + tall) ==========

    @Test
    void header80_aerial80_height190_compoundsTo1_774() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgWithHeader(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(80, 80), 190,
                Map.of(), null,
                V24ShotEventType.CORNER);

        double expected = baseline * (1.0 + 80.0 / 300.0);  // 1.4 * 1.267 = 1.774
        assertEquals(expected, xg, 0.001,
                "HEADER=80 + AERIAL=80 + height=190cm debe multiplicar baseline × 1.774");
    }

    @Test
    void header99_aerial99_height190_compoundsTo1_989() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(99, 0), null,
                Map.of(), null,
                V24ShotEventType.CORNER);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(99, 99), 190,
                Map.of(), null,
                V24ShotEventType.CORNER);

        double expected = baseline * (1.0 + 99.0 / 300.0);  // 1.495 * 1.33 = 1.989
        assertEquals(expected, xg, 0.001,
                "HEADER=99 + AERIAL=99 + height=190cm debe multiplicar baseline-HEADER × 1.33");
    }

    @Test
    void header0_aerial80_height190_appliesAerialAlone() {
        // Aplica aunque HEADER=0 — AERIAL aporta headerMult=1.267
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.CORNER);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(0, 80), 190,
                Map.of(), null,
                V24ShotEventType.CORNER);

        double expected = baseline * (1.0 + 80.0 / 300.0);  // 1.0 * 1.267 = 1.267
        assertEquals(expected, xg, 0.001,
                "HEADER=0 + AERIAL=80 + height=190cm debe dar baseline × 1.267 (AERAL solo)");
    }

    @Test
    void aerial_aloneOnCorner_xgEqualsBaselineTimes1_267() {
        // Sanity: AERIAL solo (sin HEADER) en CORNER debe dar baseline * 1.267.
        // Esto valida que AERIAL no depende de HEADER > 0 — siempre que el
        // branch CORNER/CROSS este abierto, AERIAL multiplica el headerMult
        // actual (que parte de 1.0 cuando HEADER=0).
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                Map.of(), null,
                Map.of(), null,
                V24ShotEventType.CORNER);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(0, 80), 190,
                Map.of(), null,
                V24ShotEventType.CORNER);

        assertEquals(baseline * 1.267, xg, 0.001,
                "AERIAL=80 + height=190 + HEADER=0 debe dar baseline × 1.267");
    }

    // ========== AERIAL gating (height, eventSubType, skill presence) ==========

    @Test
    void aerial80_heightBelow185_doesNotCompound() {
        // Height = 184 cm (justo debajo del umbral). AERIAL NO debe aplicar.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgWithHeader(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(80, 80), 184,
                Map.of(), null,
                V24ShotEventType.CORNER);

        assertEquals(baseline, xg, 0.001,
                "AERIAL=80 + height=184cm (bajo umbral 185) debe preservar baseline-HEADER");
    }

    @Test
    void aerial80_heightExactly185_compoundsTo1_267() {
        // Height = 185 cm (umbral exacto). AERIAL debe aplicar.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgWithHeader(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(80, 80), 185,
                Map.of(), null,
                V24ShotEventType.CORNER);

        double expected = baseline * (1.0 + 80.0 / 300.0);
        assertEquals(expected, xg, 0.001,
                "AERIAL=80 + height=185cm (umbral exacto) debe compound");
    }

    @Test
    void aerial80_heightNull_doesNotCompound() {
        // Height null (no se conoce). AERIAL NO debe aplicar (sin info,
        // no se puede saber si domina el aereo).
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgWithHeader(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(80, 80), null,
                Map.of(), null,
                V24ShotEventType.CORNER);

        assertEquals(baseline, xg, 0.001,
                "AERIAL=80 + height=null debe preservar baseline-HEADER (no se asume altura)");
    }

    @Test
    void aerial80_onOpenPlay_doesNotCompound() {
        // AERAL gating: solo aplica en CORNER/CROSS, no en OPEN_PLAY.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(80, 0), null,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(80, 80), 190,
                Map.of(), null,
                V24ShotEventType.OPEN_PLAY);

        assertEquals(baseline, xg, 0.001,
                "AERIAL=80 + height=190 en OPEN_PLAY debe preservar baseline (gated)");
    }

    @Test
    void aerial80_onCross_compounds() {
        // CROSS comparte la misma logica que CORNER.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(80, 0), null,
                Map.of(), null,
                V24ShotEventType.CROSS);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(80, 80), 190,
                Map.of(), null,
                V24ShotEventType.CROSS);

        double expected = baseline * (1.0 + 80.0 / 300.0);
        assertEquals(expected, xg, 0.001,
                "AERIAL=80 + height=190 en CROSS debe compound (igual que CORNER)");
    }

    @Test
    void aerial0_height190_doesNotCompound() {
        // AERIAL=0 (o ausente) → headerMult sin cambio.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgWithHeader(calc);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(80, 0), 190,
                Map.of(), null,
                V24ShotEventType.CORNER);

        assertEquals(baseline, xg, 0.001,
                "AERIAL=0 + height=190 debe preservar baseline-HEADER (skill 0 = no-op)");
    }

    // ========== No-op regression ==========

    @Test
    void absentAerialSkill_height190_preservesV25D33Baseline() {
        // Edge case: skills map no contiene AERIAL key. No debe tirar NPE,
        // debe preservar el resultado HEADER-only del V25D33.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();
        double baseline = baselineXgWithHeader(calc);

        Map<PlayerSkill, Integer> onlyHeader = new HashMap<>();
        onlyHeader.put(PlayerSkill.HEADER, 80);

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                onlyHeader, 190,
                Map.of(), null,
                V24ShotEventType.CORNER);

        assertEquals(baseline, xg, 0.001,
                "Sin AERAL key en skills map + height=190 debe preservar baseline V25D33");
    }

    @Test
    void overload9ArgsWithAerial_heightTall_preservesV25D32Baseline() {
        // V25D32 plumbing test: pasar AERIAL por el 9-args overload debe
        // dar el MISMO resultado que el 5-args — porque el 9-args delega al
        // 10-args con OPEN_PLAY default (AERAL gated off en OPEN_PLAY).
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        Map<PlayerSkill, Integer> manySkills = new HashMap<>();
        manySkills.put(PlayerSkill.HEADER, 80);
        manySkills.put(PlayerSkill.AERIAL, 80);

        double xg5 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0);
        double xg9 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                manySkills, 190,
                Map.of(), null);

        assertEquals(xg5, xg9, 0.0001,
                "9-args con HEADER=80 + AERIAL=80 + height=190 + OPEN_PLAY default debe preservar V25D32 baseline");
    }

    // ========== Comportamiento bajo clamp ==========

    @Test
    void headerMax_aerialMax_heightTall_isClampedToMaxXg() {
        // Verifica que HEADER + AERIAL compounding no rompe el clamp [0.01, 0.60].
        // Tomamos SIX_YARD_BOX (highest baseXg) + HEADER=99 + AERIAL=99 +
        // height=190cm → esperamos clamp a 0.60 si supera el techo.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2",
                70.0, 70.0,
                skillsWithHeaderAndAerial(99, 99), 190,
                Map.of(), null,
                V24ShotEventType.CORNER);

        assertTrue(xg <= 0.60, "xG con HEADER=99 + AERIAL=99 + height=190 en SIX_YARD_BOX debe clampear a 0.60 max");
        assertTrue(xg >= 0.01, "xG siempre >= 0.01 (clamp floor)");
    }
}