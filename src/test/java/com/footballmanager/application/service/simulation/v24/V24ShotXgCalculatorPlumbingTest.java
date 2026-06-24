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
    void overload9Args_ignoresSkillsAndHeightsInV25D32() {
        // V25D32-F4: el engine NO impact — los nuevos params (shooterSkills,
        // shooterHeightCm, gkSkills, gkHeightCm) son IGNORADOS. Si pasas skills
        // o heights distintos, el resultado es el mismo que sin ellos.
        V24ShotXgCalculator calc = new V24ShotXgCalculator();

        double xgEmpty = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                Map.of(), null, Map.of(), null);

        // Skills con levels altos (no deberia afectar)
        Map<PlayerSkill, Integer> shooterSkills = new HashMap<>();
        shooterSkills.put(PlayerSkill.SHOOTER, 99);
        shooterSkills.put(PlayerSkill.DRIBBLER, 99);
        shooterSkills.put(PlayerSkill.SPEEDSTER, 99);

        Map<PlayerSkill, Integer> gkSkills = new HashMap<>();
        gkSkills.put(PlayerSkill.WALL, 99);
        gkSkills.put(PlayerSkill.AERIAL, 99);

        double xgWithSkills = calc.calculateXg(BASELINE_QUALITY, "4-3-3", "4-4-2", 70.0, 70.0,
                shooterSkills, 200,  // shooter alto
                gkSkills, 195);      // GK alto

        assertEquals(xgEmpty, xgWithSkills, 0.0001,
                "V25D32 NO usa skills/height — el resultado debe ser identico aunque "
                + "se pasen valores extremos. Si esto cambia, V25D33 empezo a usarlos "
                + "y hay que coordinar con Mavis root.");
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
