package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D32-F4: V24ShotXgCalculator plumbing regression test.
 *
 * <p>Verifica:
 * <ul>
 *   <li>El overload 5-args (V25D27) delega al 9-args con defaults Map.of() / null
 *       y produce el MISMO resultado que el overload 2-args (regression check).
 *   <li>El overload 9-args con skills=empty + height=null produce MISMO resultado
 *       que el overload 5-args (regression check, V25D32 NO impact engine).
 *   <li>El overload 9-args con skills/heights distintos produce MISMO resultado
 *       tambien (V25D32 NO usa los nuevos params todavia).
 * </ul>
 */
class V24ShotXgCalculatorPlumbingTest {

    private static final V24ShotQuality BASELINE_QUALITY = new V24ShotQuality(
        V24ShotLocation.SIX_YARD_BOX, 0.7, 0.7, 0.3, 0.7, 1.0
    );

    @Test
    void overload5Args_delegatesTo9ArgsWithEmptyDefaults() {
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double xg5 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0);
        double xg9 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                Map.of(), null, Map.of(), null);

        assertEquals(xg5, xg9, 0.0001,
                "El overload 5-args debe dar el MISMO resultado que el 9-args con defaults");
    }

    @Test
    void overload5Args_matchesOverload2Args() {
        // V25D32-F4: regression — el overload 5-args (que delega al 9-args)
        // debe producir el mismo resultado que el overload 2-args (que tambien
        // delega al 5-args via V25D27). Ambos caminos deben converger.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double xg2 = calc.calculateXg(BASELINE_QUALITY, "4-3-3");
        double xg5 = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0);

        assertEquals(xg2, xg5, 0.0001,
                "El overload 5-args debe preservar el resultado del 2-args (no regression)");
    }

    @Test
    void overload9Args_nonImplementedSkills_remainIgnored() {
        // V25D33-F3 update: only HEADER (F1) and WALL (F3) are implemented.
        // Skills deferred to V25D34 (PLAYMAKER, AERIAL, MARKER, TACKLER,
        // SHOOTER, PASSER, SPEEDSTER) must STILL produce identical results
        // when passed via 9-args. This test replaces the V25D32-F4 "ignores
        // all skills" check with the V25D33 contract: only HEADER + WALL
        // change behavior.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double xgEmpty = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                Map.of(), null, Map.of(), null);

        // Skills deferred to V25D34 (no engine impact en F1/F2/F3)
        Map<PlayerSkill, Integer> shooterSkills = new HashMap<>();
        shooterSkills.put(PlayerSkill.PLAYMAKER, 99);
        shooterSkills.put(PlayerSkill.MARKER, 99);
        shooterSkills.put(PlayerSkill.TACKLER, 99);
        shooterSkills.put(PlayerSkill.SHOOTER, 99);
        shooterSkills.put(PlayerSkill.PASSER, 99);
        shooterSkills.put(PlayerSkill.SPEEDSTER, 99);

        Map<PlayerSkill, Integer> gkSkills = new HashMap<>();
        gkSkills.put(PlayerSkill.AERIAL, 99);

        double xgWithSkills = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                shooterSkills, 200,  // shooter alto
                gkSkills, 195);      // GK alto

        assertEquals(xgEmpty, xgWithSkills, 0.0001,
                "V25D33-F1/F3: skills NO implementados (PLAYMAKER, MARKER, "
                + "TACKLER, SHOOTER, PASSER, SPEEDSTER, AERIAL) deben seguir "
                + "sin impacto. Si esto cambia, V25D34 los empezo a usar.");
    }

    @Test
    void overload9Args_withWall99_reducesXgByWallDivisor() {
        // V25D33-F3: WALL=99 en gkSkills debe REDUCIR el xG via divisor.
        // El 9-args overload delega al 10-args con OPEN_PLAY (HEADER gated off)
        // + gkSkills={WALL:99}, por lo que el WALL divisor SI aplica.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double xgNoWall = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                Map.of(), null, Map.of(), null);

        Map<PlayerSkill, Integer> gkSkills = new HashMap<>();
        gkSkills.put(PlayerSkill.WALL, 99);

        double xgWithWall = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                Map.of(), null, gkSkills, null);

        // WALL=99 → divisor = 1/(1+99/150) = 1/1.66 ≈ 0.602
        // El xG sin clamp es baseline × 0.602. Pero el clamp [0.01, 0.60]
        // puede saturar el resultado — verificamos que es <= baseline y >= 0.01.
        assertTrue(xgWithWall < xgNoWall,
                "WALL=99 debe REDUCIR xG (actual: noWall=" + xgNoWall + " withWall=" + xgWithWall + ")");
        assertTrue(xgWithWall >= 0.01, "xG debe seguir >= 0.01 (clamp floor)");
        assertTrue(xgWithWall <= 0.60, "xG debe seguir <= 0.60 (clamp ceiling)");
    }

    @Test
    void overload9Args_nullSkillsIsAccepted() {
        // Null skills / null height son casos validos (player random del seed
        // sin curated skills). El engine debe aceptarlos sin NPE.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double xg = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                null, null, null, null);

        assertTrue(xg > 0, "Calculo debe completar sin NPE cuando skills/height son null");
        assertTrue(xg <= 0.60, "xG debe estar en [0.01, 0.60] como siempre");
    }

    @Test
    void overload9Args_resultMatchesOverload5ArgsForRealisticInputs() {
        // Test con varios inputs realistas (diferentes formations, possessorAttack,
        // opponentDefense) — los overloads 5-args y 9-args deben coincidir.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        String[][] formations = {
            {"4-3-3", "4-4-2"}, {"4-2-3-1", "5-3-2"},
            {"3-5-2", "4-3-3"}, {"4-4-2", "3-4-3"}
        };
        double[] attacks = {55, 70, 85};
        double[] defenses = {55, 70, 85};

        for (String[] f : formations) {
            for (double att : attacks) {
                for (double def : defenses) {
                    double xg5 = calc.calculateXg(BASELINE_QUALITY, f[0], f[1], att, def);
                    double xg9 = calc.calculateXg(BASELINE_QUALITY, f[0], f[1], att, def,
                            Map.of(), null, Map.of(), null);
                    assertEquals(xg5, xg9, 0.0001,
                            "Mismatch para formation=" + f[0] + "/" + f[1]
                            + " att=" + att + " def=" + def);
                }
            }
        }
    }
}
