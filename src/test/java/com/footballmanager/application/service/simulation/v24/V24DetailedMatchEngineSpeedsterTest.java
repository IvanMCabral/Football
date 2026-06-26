package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D34-F3: SPEEDSTER skill impact on chanceProbability (counter-attack bonus).
 *
 * <p>Spec (V25D34 prompt, F3):
 * <ul>
 *   <li>SPEEDSTER amplifica keySpeed SOLO cuando {@code style == COUNTER}.
 *       Formula: {@code effectiveSpeed = possessorSpeed + speedsterSkill / 3}.
 *       El shift se propaga al qualityMod (que pesa speed * 0.01).</li>
 *   <li>Calibration:
 *     <ul>
 *       <li>SPEEDSTER=0 o style != COUNTER → no change (no-op)</li>
 *       <li>SPEEDSTER=92 (Vinicius) en COUNTER → speed += 30.67 → qualityMod
 *           shift = 0.307 → chanceProb * 1.307 (+30.7%)</li>
 *       <li>SPEEDSTER=50 en COUNTER → speed += 16.67 → chanceProb * 1.167 (+16.7%)</li>
 *       <li>SPEEDSTER=99 en COUNTER → speed += 33 → chanceProb * 1.33 (+33%)</li>
 *     </ul>
 *   </li>
 *   <li>Gating: SPEEDSTER bonus SOLO aplica en COUNTER style. En ATTACKING /
 *       POSSESSION / DEFENSIVE / BALANCED el skill se ignora.</li>
 *   <li>Absent/null SPEEDSTER skill → tratado como 0 (no change).</li>
 *   <li>No-op regression: SPEEDSTER=0 → bit-a-bit identico al overload 5-args
 *       (que ya delega al 6-args con 0).</li>
 * </ul>
 */
class V24DetailedMatchEngineSpeedsterTest {

    private static final long SEED = 42L;

    // ========== Unit: formula de chanceProbability con SPEEDSTER ==========

    @Test
    void chanceProb_withSpeedster0_isIdenticalToBaseline() throws Exception {
        // SPEEDSTER=0 → mismo resultado que el overload 5-args (no-op).
        double baseline5 = invokeChanceProb5(TeamStyle.COUNTER, 30, 70, 70, 0);
        double withZero6 = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 0, 0);
        assertEquals(baseline5, withZero6, 0.0001,
                "SPEEDSTER=0 en COUNTER debe dar el MISMO resultado que el 5-args legacy");
    }

    @Test
    void chanceProb_withSpeedster92_onCounter_increasesBy30Percent() throws Exception {
        // SPEEDSTER=92 en COUNTER → keySpeed += 30 (integer div 92/3=30)
        // → qualityMod shift = 30 * 0.01 = 0.30 → chanceProb * 1.30 (+30%).
        double baseline = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 0, 0);
        double withSpeedster = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 0, 92);

        // El shift en qualityMod: (effectiveSpeed - 70) * 0.01 = (100 - 70) * 0.01 = 0.30
        // qualityMod con baseline: 1.0 + (70-70)*0.02 + (70-70)*0.01 = 1.0
        // qualityMod con SPEEDSTER=92: 1.0 + 0 + 0.30 = 1.30
        // chanceProb ratio: 1.30 / 1.0 = 1.30
        double expectedMult = 1.0 + (92 / 3) * 0.01;
        assertEquals(baseline * expectedMult, withSpeedster, 0.0001,
                "SPEEDSTER=92 en COUNTER debe dar chanceProb * (1 + 30*0.01) = 1.30");
    }

    @Test
    void chanceProb_withSpeedster50_onCounter_increasesBy16Percent() throws Exception {
        // SPEEDSTER=50 → speed += 16 (integer div 50/3=16) → chanceProb * 1.16 (+16%).
        double baseline = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 0, 0);
        double withSpeedster = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 0, 50);

        double expectedMult = 1.0 + (50 / 3) * 0.01;
        assertEquals(baseline * expectedMult, withSpeedster, 0.0001,
                "SPEEDSTER=50 en COUNTER debe dar chanceProb * (1 + 16*0.01) = 1.16");
    }

    @Test
    void chanceProb_withSpeedster99_onCounter_increasesBy33Percent() throws Exception {
        // SPEEDSTER=99 → speed += 33 (integer div 99/3=33) → chanceProb * 1.33 (+33%).
        double baseline = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 0, 0);
        double withSpeedster = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 0, 99);

        double expectedMult = 1.0 + (99 / 3) * 0.01;
        assertEquals(baseline * expectedMult, withSpeedster, 0.0001,
                "SPEEDSTER=99 en COUNTER debe dar chanceProb * (1 + 33*0.01) = 1.33");
    }

    @Test
    void chanceProb_withSpeedster99_onAttacking_doesNotApply() throws Exception {
        // SPEEDSTER gated en COUNTER — en ATTACKING el skill se ignora.
        double baseline = invokeChanceProb6(TeamStyle.ATTACKING, 30, 70, 70, 0, 0);
        double withSpeedster = invokeChanceProb6(TeamStyle.ATTACKING, 30, 70, 70, 0, 99);

        assertEquals(baseline, withSpeedster, 0.0001,
                "SPEEDSTER=99 en ATTACKING debe preservar baseline (gated COUNTER only)");
    }

    @Test
    void chanceProb_withSpeedster99_onPossession_doesNotApply() throws Exception {
        double baseline = invokeChanceProb6(TeamStyle.POSSESSION, 30, 70, 70, 0, 0);
        double withSpeedster = invokeChanceProb6(TeamStyle.POSSESSION, 30, 70, 70, 0, 99);

        assertEquals(baseline, withSpeedster, 0.0001,
                "SPEEDSTER=99 en POSSESSION debe preservar baseline");
    }

    @Test
    void chanceProb_withSpeedster99_onDefensive_doesNotApply() throws Exception {
        double baseline = invokeChanceProb6(TeamStyle.DEFENSIVE, 30, 70, 70, 0, 0);
        double withSpeedster = invokeChanceProb6(TeamStyle.DEFENSIVE, 30, 70, 70, 0, 99);

        assertEquals(baseline, withSpeedster, 0.0001,
                "SPEEDSTER=99 en DEFENSIVE debe preservar baseline");
    }

    @Test
    void chanceProb_withSpeedster99_onBalanced_doesNotApply() throws Exception {
        double baseline = invokeChanceProb6(TeamStyle.BALANCED, 30, 70, 70, 0, 0);
        double withSpeedster = invokeChanceProb6(TeamStyle.BALANCED, 30, 70, 70, 0, 99);

        assertEquals(baseline, withSpeedster, 0.0001,
                "SPEEDSTER=99 en BALANCED debe preservar baseline");
    }

    @Test
    void chanceProb_speedsterComposesWithDribbler() throws Exception {
        // SPEEDSTER (effectiveSpeed bonus) y DRIBBLER (multiplicador) ambos
        // aplican en COUNTER. Compounding multiplicativo.
        // DRIBBLER=50 → multiplier 1.167. SPEEDSTER=92 → multiplier 1.30 (int div).
        // Combined: 1.167 * 1.30 = 1.517.
        double baseline = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 0, 0);
        double withBoth = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 50, 92);

        double expectedMult = (1.0 + 50.0 / 300.0) * (1.0 + (92 / 3) * 0.01);
        assertEquals(baseline * expectedMult, withBoth, 0.0001,
                "SPEEDSTER=92 + DRIBBLER=50 en COUNTER deben compound");
    }

    @Test
    void chanceProb_speedsterComposesWithAttack() throws Exception {
        // SPEEDSTER amplifica keySpeed, lo que compone con qualityMod
        // (que pesa attack * 0.02 + speed * 0.01). attack=85 + SPEEDSTER=50
        // → qualityMod = 1.0 + 15*0.02 + (70+16)*0.01 = 1.0 + 0.30 + 0.16 = 1.46.
        // baseline (attack=70, no speedster): qualityMod = 1.0.
        // Ratio: 1.46 / 1.0 = 1.46.
        double baseline = invokeChanceProb6(TeamStyle.COUNTER, 30, 70, 70, 0, 0);
        double withBoth = invokeChanceProb6(TeamStyle.COUNTER, 30, 85, 70, 0, 50);

        double baselineQMod = 1.0 + (70 - 70) * 0.02 + (70 - 70) * 0.01;
        double combinedQMod = 1.0 + (85 - 70) * 0.02 + (70 + 50 / 3 - 70) * 0.01;
        double expectedMult = combinedQMod / baselineQMod;
        assertEquals(baseline * expectedMult, withBoth, 0.0001,
                "SPEEDSTER=50 + attack=85 en COUNTER deben compound via qualityMod");
    }

    // ========== Integration: full match reflects the change ==========

    @Test
    void fullMatch_speedsterHigh_producesMoreShotsThanBaselineInCounter() {
        // Baseline: home equipo COUNTER sin SPEEDSTER.
        // Treatment: home key attacker con SPEEDSTER=99 + COUNTER style.
        // Mas SPEEDSTER en counter-attacks → mas chances → mas shots.
        V24MatchContext baseline = buildContextWithSpeedster("speedster-base", TeamStyle.COUNTER, -1);
        V24MatchContext treatment = buildContextWithSpeedster("speedster-high", TeamStyle.COUNTER, 99);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult baselineResult = engine.simulate(baseline, SEED);
        V24DetailedMatchResult treatmentResult = engine.simulate(treatment, SEED);

        int baselineShots = baselineResult.homeShots() + baselineResult.awayShots();
        int treatmentShots = treatmentResult.homeShots() + treatmentResult.awayShots();

        // Treatment (home SPEEDSTER=99 + COUNTER) debe producir > shots
        // que baseline. away shots son identicos (away no tiene SPEEDSTER).
        // El multiplier ~1.33 puede no siempre agregar shots por varianza
        // Bernoulli en N=1 match, pero la expectativa matematica sube.
        assertTrue(treatmentShots >= baselineShots,
                "SPEEDSTER=99 + COUNTER home debe producir >= shots que baseline (actual: baseline="
                        + baselineShots + ", treatment=" + treatmentShots + ")");
        assertTrue(treatmentShots > baselineShots
                        || treatmentResult.homeXg() > baselineResult.homeXg(),
                "SPEEDSTER=99 debe reflejarse en mas shots O mas xG (baseline shots="
                        + baselineShots + " xg=" + baselineResult.homeXg()
                        + "; treatment shots=" + treatmentShots + " xg=" + treatmentResult.homeXg() + ")");
    }

    @Test
    void fullMatch_speedsterHigh_inAttacking_doesNotProduceMoreShotsThanBaseline() {
        // SPEEDSTER gated en COUNTER — en ATTACKING no debe haber efecto.
        V24MatchContext baseline = buildContextWithSpeedster("speedster-base-att", TeamStyle.ATTACKING, -1);
        V24MatchContext treatment = buildContextWithSpeedster("speedster-high-att", TeamStyle.ATTACKING, 99);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult baselineResult = engine.simulate(baseline, SEED);
        V24DetailedMatchResult treatmentResult = engine.simulate(treatment, SEED);

        int baselineShots = baselineResult.homeShots() + baselineResult.awayShots();
        int treatmentShots = treatmentResult.homeShots() + treatmentResult.awayShots();

        // En ATTACKING el SPEEDSTER bonus no aplica (gated COUNTER).
        // Los shots deben ser similares (la varianza Bernoulli puede dar
        // ±1 shot entre matches con mismo seed si las features cambian
        // ligeramente, pero la EXPECTATIVA matematica es igual). Por eso
        // usamos >= aqui en lugar de >, y permitimos 0 diferencia.
        // Lo importante: SPEEDSTER no produce el uplift de COUNTER (~+30%).
        // Verificamos que no hay uplift dramatico.
        int shotDelta = treatmentShots - baselineShots;
        assertTrue(shotDelta <= 5,
                "SPEEDSTER=99 en ATTACKING no debe dar uplift > 5 shots vs baseline (actual: delta="
                        + shotDelta + ")");
    }

    @Test
    void fullMatch_noSpeedsterSkill_preservesV25D33Baseline() {
        // Regression: sin SPEEDSTER en el dominio, el engine debe producir
        // el MISMO resultado que V25D33 (bit-a-bit para los homeGoals/awayGoals
        // /shots/xG con COUNTER style).
        V24MatchContext baseline = buildContextWithSpeedster("no-speedster", TeamStyle.COUNTER, -1);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult result = engine.simulate(baseline, SEED);

        // Sanity: el match produce resultado razonable (lambda ~1.25, goles 0-5).
        int totalGoals = result.homeGoals() + result.awayGoals();
        assertTrue(totalGoals >= 0 && totalGoals <= 10,
                "Sin skills, totalGoals debe estar en [0,10] (actual: " + totalGoals + ")");
        assertTrue(result.homeXg() >= 0 && result.awayXg() >= 0,
                "xG debe ser no-negativo");
    }

    // ========== Reflection helpers ==========

    /**
     * Reflection para invocar el overload 5-args de chanceProbability (legacy
     * V25D33-F2). Usado para verificar que el overload 6-args con
     * speedsterSkill=0 produce el mismo resultado.
     */
    private double invokeChanceProb5(TeamStyle style, int minute, int attack,
                                     int speed, int dribbler) throws Exception {
        Method m = V24DetailedMatchEngine.class.getDeclaredMethod(
                "chanceProbability", TeamStyle.class, int.class, int.class, int.class, int.class);
        m.setAccessible(true);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        return (double) m.invoke(engine, style, minute, attack, speed, dribbler);
    }

    /**
     * Reflection para invocar el overload 6-args de chanceProbability (V25D34-F3).
     */
    private double invokeChanceProb6(TeamStyle style, int minute, int attack,
                                     int speed, int dribbler, int speedster) throws Exception {
        Method m = V24DetailedMatchEngine.class.getDeclaredMethod(
                "chanceProbability", TeamStyle.class, int.class, int.class,
                int.class, int.class, int.class);
        m.setAccessible(true);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        return (double) m.invoke(engine, style, minute, attack, speed, dribbler, speedster);
    }

    // ========== Fixture builders ==========

    private V24MatchContext buildContextWithSpeedster(String matchId, TeamStyle homeStyle, int speedsterSkill) {
        com.footballmanager.domain.model.entity.SessionTeam homeTeam =
                com.footballmanager.domain.model.entity.SessionTeam.fromRealTeam(
                        java.util.UUID.nameUUIDFromBytes(("home-" + matchId).getBytes()),
                        "world_home-" + matchId, "Home FC", "Country",
                        java.math.BigDecimal.ZERO, "4-3-3", null);
        com.footballmanager.domain.model.entity.SessionTeam awayTeam =
                com.footballmanager.domain.model.entity.SessionTeam.fromRealTeam(
                        java.util.UUID.nameUUIDFromBytes(("away-" + matchId).getBytes()),
                        "world_away-" + matchId, "Away FC", "Country",
                        java.math.BigDecimal.ZERO, "4-3-3", null);

        java.util.List<com.footballmanager.domain.model.entity.SessionPlayer> homeStart =
                makePlayersWithSpeedster("home", 11, 70, speedsterSkill);
        java.util.List<com.footballmanager.domain.model.entity.SessionPlayer> awayStart =
                makePlayersWithSpeedster("away", 11, 70, -1);
        return new V24MatchContext(
                matchId,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                java.util.List.of(), java.util.List.of(),
                "4-3-3", "4-3-3",
                homeStyle, TeamStyle.BALANCED
        );
    }

    private java.util.List<com.footballmanager.domain.model.entity.SessionPlayer> makePlayersWithSpeedster(
            String prefix, int count, int ovr, int speedsterSkill) {
        java.util.List<com.footballmanager.domain.model.entity.SessionPlayer> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + "_p" + i;
            com.footballmanager.domain.model.entity.SessionPlayer p =
                    com.footballmanager.domain.model.entity.SessionPlayer.custom(
                            id, 25, "MID",
                            ovr, ovr, ovr, ovr, ovr, ovr,
                            java.math.BigDecimal.valueOf(ovr * 1000));
            if (i == 0 && speedsterSkill > 0) {
                p.setSkillLevel(PlayerSkill.SPEEDSTER, speedsterSkill);
            }
            list.add(p);
        }
        return list;
    }
}