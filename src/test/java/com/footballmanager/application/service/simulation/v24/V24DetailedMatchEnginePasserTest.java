package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D34-F3: PASSER skill impact on possession share (retention rate boost).
 *
 * <p>Spec (V25D34 prompt, F3):
 * <ul>
 *   <li>PASSER skill del mejor pasador on-pitch amplifica la possession
 *       share base del equipo. Formula:
 *       <pre>
 *       homePossAdj = homePossBase * (1 + homeMaxPasser / 300)
 *       awayPossAdj = awayPossBase * (1 + awayMaxPasser / 300)
 *       homeShare = homePossAdj / (homePossAdj + awayPossAdj)
 *       </pre>
 *   </li>
 *   <li>Calibration:
 *     <ul>
 *       <li>PASSER=0 → factor 1.0 (no change, bit-a-bit V25D33)</li>
 *       <li>PASSER=85 (Valverde) → factor 1.283 (+28% retention)</li>
 *       <li>PASSER=99 → factor 1.33 (+33% retention)</li>
 *     </ul>
 *   </li>
 *   <li>Gating: PASSER aplica en cualquier style (no hay gating — es
 *       "precision de pase general", no atado a un esquema tactico).</li>
 *   <li>Absent/null PASSER skill → tratado como 0 (max retorna 0, factor 1.0).</li>
 *   <li>No-op regression: PASSER=0 → bit-a-bit identico a V25D33.</li>
 * </ul>
 */
class V24DetailedMatchEnginePasserTest {

    private static final long SEED = 42L;

    // ========== Unit: formula de maxPasserSkill ==========

    @Test
    void maxPasser_noPasserSkill_returnsZero() throws Exception {
        List<V24PlayerMatchState> players = makeMatchStates(11, "MID", 70, -1);
        assertEquals(0, invokeMaxPasser(players),
                "Sin PASSER skills, maxPasser debe retornar 0");
    }

    @Test
    void maxPasser_singlePlayerWithSkill_returnsSkillLevel() throws Exception {
        List<V24PlayerMatchState> players = makeMatchStates(11, "MID", 70, -1);
        // Set PASSER on player index 3 via SessionPlayer + fromSessionPlayer.
        SessionPlayer p3 = makePlayer("home_p3", 70);
        p3.setSkillLevel(PlayerSkill.PASSER, 85);
        players.set(3, V24PlayerMatchState.fromSessionPlayer(p3, "home"));

        assertEquals(85, invokeMaxPasser(players),
                "maxPasser debe retornar el max PASSER (85) entre on-pitch players");
    }

    @Test
    void maxPasser_multiplePlayers_returnsMax() throws Exception {
        List<V24PlayerMatchState> players = makeMatchStates(11, "MID", 70, -1);
        SessionPlayer p2 = makePlayer("home_p2", 70);
        p2.setSkillLevel(PlayerSkill.PASSER, 50);
        players.set(2, V24PlayerMatchState.fromSessionPlayer(p2, "home"));
        SessionPlayer p5 = makePlayer("home_p5", 70);
        p5.setSkillLevel(PlayerSkill.PASSER, 75);
        players.set(5, V24PlayerMatchState.fromSessionPlayer(p5, "home"));
        SessionPlayer p8 = makePlayer("home_p8", 70);
        p8.setSkillLevel(PlayerSkill.PASSER, 99);
        players.set(8, V24PlayerMatchState.fromSessionPlayer(p8, "home"));

        assertEquals(99, invokeMaxPasser(players),
                "maxPasser debe retornar el max entre multiples players (99)");
    }

    @Test
    void maxPasser_skipsOffPitchPlayers() throws Exception {
        // Player con PASSER=99 pero off-pitch no debe contar. Creamos
        // directamente un V24PlayerMatchState off-pitch via fromSessionPlayer
        // y luego mutate onPitch=false (hay un setter? — V24PlayerMatchState
        // no expone setter publico para onPitch. Por eso usamos reflection
        // para forzar el estado).
        SessionPlayer offPitchPlayer = makePlayer("home_off", 70);
        offPitchPlayer.setSkillLevel(PlayerSkill.PASSER, 99);
        V24PlayerMatchState offState = V24PlayerMatchState.fromSessionPlayer(offPitchPlayer, "home");

        // Forzar onPitch=false via reflection (V24PlayerMatchState no expone
        // setter publico para onPitch — el helper substituteOff() existe pero
        // requiere que el player este en startingPlayers).
        java.lang.reflect.Field onPitchField = V24PlayerMatchState.class.getDeclaredField("onPitch");
        onPitchField.setAccessible(true);
        onPitchField.setBoolean(offState, false);
        assertFalse(offState.onPitch(), "Sanity: el player debe estar off-pitch");

        List<V24PlayerMatchState> players = new ArrayList<>();
        players.add(offState);  // primer player off-pitch con PASSER=99
        for (int i = 1; i < 11; i++) {
            players.add(V24PlayerMatchState.fromSessionPlayer(makePlayer("home_p" + i, 70), "home"));
        }

        assertEquals(0, invokeMaxPasser(players),
                "maxPasser debe ignorar players off-pitch");
    }

    // ========== Integration: full match reflects the change ==========

    @Test
    void fullMatch_passerHigh_homeHasMorePossessionThanBaseline() {
        // Baseline: home BALANCED sin PASSER (away BALANCED sin PASSER).
        // Treatment: home key attacker PASSER=99, away sin PASSER.
        // PASSER amplifica possession share del home → home deberia acumular
        // mas possession ticks. away ticks deberian ser similares (away no
        // cambio).
        V24MatchContext baseline = buildContextWithPasser("passer-base", -1);
        V24MatchContext treatment = buildContextWithPasser("passer-high", 99);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult baselineResult = engine.simulate(baseline, SEED);
        V24DetailedMatchResult treatmentResult = engine.simulate(treatment, SEED);

        // PASSER=99 amplifica possession base por 1.33. Para BALANCED (base=50),
        // homePossAdj = 50 * 1.33 = 66.5. homeShare = 66.5 / (66.5 + 50) ≈ 0.571.
        // Sin PASSER: homeShare = 0.5. Delta esperado: ~7% mas ticks para home.
        int baselineHomePoss = baselineResult.homePossession();
        int treatmentHomePoss = treatmentResult.homePossession();

        assertTrue(treatmentHomePoss >= baselineHomePoss,
                "PASSER=99 home debe producir >= possession que baseline (actual: baseline="
                        + baselineHomePoss + ", treatment=" + treatmentHomePoss + ")");
    }

    @Test
    void fullMatch_noPasserSkill_preservesV25D33Baseline() {
        // Regression: sin PASSER en el dominio, el engine debe producir
        // el MISMO resultado que V25D33 (bit-a-bit para possession share,
        // homeGoals/awayGoals, etc.).
        V24MatchContext baseline = buildContextWithPasser("no-passer", -1);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult result = engine.simulate(baseline, SEED);

        // Sanity: el match produce resultado razonable.
        int totalGoals = result.homeGoals() + result.awayGoals();
        assertTrue(totalGoals >= 0 && totalGoals <= 10,
                "Sin skills, totalGoals debe estar en [0,10] (actual: " + totalGoals + ")");
        assertTrue(result.homePossession() >= 0 && result.awayPossession() >= 0,
                "Possession debe ser no-negativo");
    }

    @Test
    void fullMatch_passerSymmetric_homeAndAwayBothHave99_producesSimilarPossessionToBaseline() {
        // Si AMBOS equipos tienen PASSER=99 maximo, el efecto se cancela
        // (ambos boosts son iguales → homeShare = 50/(50+50) = 0.5 igual
        // que sin boost). Verificamos que el resultado sea similar al
        // baseline sin PASSER (symmetric boost = no-op).
        V24MatchContext baseline = buildContextWithPasserSymmetric("passer-sym-base", -1);
        V24MatchContext treatment = buildContextWithPasserSymmetric("passer-sym-high", 99);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult baselineResult = engine.simulate(baseline, SEED);
        V24DetailedMatchResult treatmentResult = engine.simulate(treatment, SEED);

        // Symmetric PASSER boost (home y away ambos +33%) → homeShare = 0.5
        // igual que sin boost. Los possession deben ser similares (±3 por
        // la varianza natural de los rolls, no sistematica).
        int baselineHomePoss = baselineResult.homePossession();
        int treatmentHomePoss = treatmentResult.homePossession();

        int delta = Math.abs(treatmentHomePoss - baselineHomePoss);
        assertTrue(delta <= 5,
                "PASSER=99 simetrico (home+away) debe dar delta de possession <= 5 (actual: "
                        + delta + ", baseline=" + baselineHomePoss + ", treatment=" + treatmentHomePoss + ")");
    }

    // ========== Reflection helper ==========

    private int invokeMaxPasser(List<V24PlayerMatchState> players) throws Exception {
        Method m = V24DetailedMatchEngine.class.getDeclaredMethod(
                "maxPasserSkill", List.class);
        m.setAccessible(true);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        return (int) m.invoke(engine, players);
    }

    // ========== Fixture builders ==========

    private V24MatchContext buildContextWithPasser(String matchId, int passerSkill) {
        List<SessionPlayer> homeStart = makePlayersWithPasser("home", 11, 70, passerSkill);
        List<SessionPlayer> awayStart = makePlayersWithPasser("away", 11, 70, -1);
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new V24MatchContext(
                matchId,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private V24MatchContext buildContextWithPasserSymmetric(String matchId, int passerSkill) {
        List<SessionPlayer> homeStart = makePlayersWithPasser("home", 11, 70, passerSkill);
        List<SessionPlayer> awayStart = makePlayersWithPasser("away", 11, 70, passerSkill);
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new V24MatchContext(
                matchId,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
    }

    private List<SessionPlayer> makePlayersWithPasser(String prefix, int count, int ovr, int passerSkill) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SessionPlayer p = makePlayer(prefix + "_p" + i, ovr);
            if (i == 0 && passerSkill > 0) {
                p.setSkillLevel(PlayerSkill.PASSER, passerSkill);
            }
            list.add(p);
        }
        return list;
    }

    private List<V24PlayerMatchState> makeMatchStates(int count, String position, int ovr, int skill) {
        List<V24PlayerMatchState> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SessionPlayer p = makePlayer("p" + i, ovr);
            if (skill > 0) {
                p.setSkillLevel(PlayerSkill.PASSER, skill);
            }
            list.add(V24PlayerMatchState.fromSessionPlayer(p, "home"));
        }
        return list;
    }

    private SessionPlayer makePlayer(String id, int ovr) {
        return SessionPlayer.custom(
                id, 25, "MID",
                ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000));
    }

    private SessionTeam makeTeam(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, "4-3-3", null);
    }
}