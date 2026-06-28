package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V25D69-C29 Phase 1b: TeamStyle effect diagnostic.
 *
 * <p>After Phase 1 REJECTED skills-amplify hypothesis (skills REDUCED goals to
 * 0.31x of baseline), investigate whether TeamStyle (BALANCED vs ATTACKING vs
 * COUNTER) is the missing factor that explains the diagnostic-vs-runtime gap.
 *
 * <p>Hypothesis: top teams in REVISOR's smoke user probably use ATTACKING
 * (base chanceProb 0.42 vs BALANCED 0.35 = +20% shots). COUNTER adds
 * SPEEDSTER bonus (+33% chanceProb when speedster skill > 0). Combined with
 * offensive skills (DRIBBLER +30%, PASSER +28% possession share), the
 * cumulative multiplier could approach 2-2.5x of BALANCED.
 *
 * <p>Profile: same mixed skills as V29SkillsAmplificationDiagnosticTest.
 * 15 measurements: 5 scenarios x 3 styles.
 *
 * <p>Usage: mvn test -Dtest=V29bStyleEffectDiagnosticTest -DfailIfNoTests=false
 */
class V29bStyleEffectDiagnosticTest {

    private static final int N_SIMULATIONS = 200;

    private static Map<PlayerSkill, Integer> skillProfileForIndex(int index) {
        return switch (index) {
            case 0 -> Map.of(PlayerSkill.WALL, 85);
            case 1, 2, 3, 4 -> Map.of(PlayerSkill.MARKER, 75, PlayerSkill.TACKLER, 70);
            case 5, 6, 7, 8 -> Map.of(PlayerSkill.PASSER, 80, PlayerSkill.DRIBBLER, 70);
            case 9, 10 -> Map.of(PlayerSkill.DRIBBLER, 90, PlayerSkill.SHOOTER, 85,
                    PlayerSkill.HEADER, 70, PlayerSkill.AERIAL, 70);
            default -> Map.of();
        };
    }

    private static String positionForIndex(int index) {
        if (index == 0) return "GK";
        if (index >= 1 && index <= 4) return "DEF";
        if (index >= 5 && index <= 8) return "MID";
        return "ATT";
    }

    private static Integer heightForIndex(int index) {
        return (index == 9 || index == 10) ? 185 : null;
    }

    // ========== ATTACKING style ==========

    @Test @DisplayName("V29b: PAREJOS 85x85 + ATTACKING")
    void attacking_parejos() {
        runScenario(TeamStyle.ATTACKING, "PAREJOS", 85, 85, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: INTERMEDIO-A 85x75 + ATTACKING")
    void attacking_intermedioA() {
        runScenario(TeamStyle.ATTACKING, "INTERMEDIO-A", 85, 75, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: INTERMEDIO-B 85x70 + ATTACKING")
    void attacking_intermedioB() {
        runScenario(TeamStyle.ATTACKING, "INTERMEDIO-B", 85, 70, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: INTERMEDIO-C 85x65 + ATTACKING")
    void attacking_intermedioC() {
        runScenario(TeamStyle.ATTACKING, "INTERMEDIO-C", 85, 65, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: DESIGUALES 90x60 + ATTACKING")
    void attacking_desiguales() {
        runScenario(TeamStyle.ATTACKING, "DESIGUALES", 90, 60, "4-3-3", "5-3-2");
    }

    // ========== COUNTER style ==========

    @Test @DisplayName("V29b: PAREJOS 85x85 + COUNTER")
    void counter_parejos() {
        runScenario(TeamStyle.COUNTER, "PAREJOS", 85, 85, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: INTERMEDIO-A 85x75 + COUNTER")
    void counter_intermedioA() {
        runScenario(TeamStyle.COUNTER, "INTERMEDIO-A", 85, 75, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: INTERMEDIO-B 85x70 + COUNTER")
    void counter_intermedioB() {
        runScenario(TeamStyle.COUNTER, "INTERMEDIO-B", 85, 70, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: INTERMEDIO-C 85x65 + COUNTER")
    void counter_intermedioC() {
        runScenario(TeamStyle.COUNTER, "INTERMEDIO-C", 85, 65, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: DESIGUALES 90x60 + COUNTER")
    void counter_desiguales() {
        runScenario(TeamStyle.COUNTER, "DESIGUALES", 90, 60, "4-3-3", "5-3-2");
    }

    // ========== BALANCED style (re-run with skills for comparison) ==========

    @Test @DisplayName("V29b: PAREJOS 85x85 + BALANCED (skills ON, baseline)")
    void balanced_parejos() {
        runScenario(TeamStyle.BALANCED, "PAREJOS", 85, 85, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: INTERMEDIO-A 85x75 + BALANCED (skills ON, baseline)")
    void balanced_intermedioA() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-A", 85, 75, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: INTERMEDIO-B 85x70 + BALANCED (skills ON, baseline)")
    void balanced_intermedioB() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-B", 85, 70, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: INTERMEDIO-C 85x65 + BALANCED (skills ON, baseline)")
    void balanced_intermedioC() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-C", 85, 65, "4-3-3", "4-3-3");
    }

    @Test @DisplayName("V29b: DESIGUALES 90x60 + BALANCED (skills ON, baseline)")
    void balanced_desiguales() {
        runScenario(TeamStyle.BALANCED, "DESIGUALES", 90, 60, "4-3-3", "5-3-2");
    }

    // ========== Phase 1b summary ==========

    @Test
    @Disabled("V25D69-C29 research artifact — superseded by V29d. ENABLE to re-run.")
    @DisplayName("V29b PHASE-1b SUMMARY: style amplification vs BALANCED")
    void phase1b_summary() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D69-C29 PHASE-1b STYLE EFFECT SUMMARY");
        System.out.println("================================================================");

        // Re-run all 3 styles for intermedios (A/B/C) and compute averages
        double balSum = 0, attSum = 0, couSum = 0;
        String[] labels = {"INTERMEDIO-A 85x75", "INTERMEDIO-B 85x70", "INTERMEDIO-C 85x65"};
        double[] balVals = new double[3], attVals = new double[3], couVals = new double[3];

        for (int i = 0; i < 3; i++) {
            int homeOvr = 85;
            int awayOvr = (i == 0) ? 75 : (i == 1) ? 70 : 65;
            String label = labels[i];
            balVals[i] = runAndAverage(engine, TeamStyle.BALANCED, homeOvr, awayOvr);
            attVals[i] = runAndAverage(engine, TeamStyle.ATTACKING, homeOvr, awayOvr);
            couVals[i] = runAndAverage(engine, TeamStyle.COUNTER, homeOvr, awayOvr);
            balSum += balVals[i];
            attSum += attVals[i];
            couSum += couVals[i];
            System.out.printf("%s: BALANCED=%.3f  ATTACKING=%.3f  COUNTER=%.3f%n",
                    label, balVals[i], attVals[i], couVals[i]);
        }

        double balAvg = balSum / 3.0;
        double attAvg = attSum / 3.0;
        double couAvg = couSum / 3.0;
        double baselineNoSkills = 2.57; // V27 diagnostic post-C28, no skills

        System.out.println();
        System.out.println("INTERMEDIOS AVG (A+B+C)");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("BALANCED  (skills ON, baseline):    %.3f%n", balAvg);
        System.out.printf("ATTACKING (skills ON):             %.3f (x%.2f vs BALANCED, x%.2f vs no-skills baseline %.2f)%n",
                attAvg, attAvg / balAvg, attAvg / baselineNoSkills, baselineNoSkills);
        System.out.printf("COUNTER   (skills ON):             %.3f (x%.2f vs BALANCED, x%.2f vs no-skills baseline %.2f)%n",
                couAvg, couAvg / balAvg, couAvg / baselineNoSkills, baselineNoSkills);
        System.out.println();
        System.out.println("Runtime observed (REVISOR post-C28): intermedios avg ~6.5");
        System.out.printf("  ATTACKING gap runtime vs diagnostic-with-skills: %.3fx%n", 6.5 / attAvg);
        System.out.printf("  COUNTER   gap runtime vs diagnostic-with-skills: %.3fx%n", 6.5 / couAvg);
        System.out.println("================================================================");

        // Phase 1b confirmation: at least one of ATTACKING/COUNTER must
        // amplify intermedios by >=1.5x vs BALANCED skills-on baseline.
        double attAmp = attAvg / balAvg;
        double couAmp = couAvg / balAvg;
        assertTrue(attAmp >= 1.5 || couAmp >= 1.5,
                "V25D69-C29 PHASE-1b: at least one style must amplify intermedios by >=1.5x. " +
                "Got attAmp=" + attAmp + ", couAmp=" + couAmp +
                " (attRaw=" + attAvg + ", couRaw=" + couAvg + ", balRaw=" + balAvg + ")");
    }

    // ========== Helpers ==========

    private void runScenario(TeamStyle style, String label, int homeOvr, int awayOvr,
                             String homeFormation, String awayFormation) {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        long totalGoals = 0;
        long totalHomeShots = 0, totalAwayShots = 0;
        double totalHomeXg = 0, totalAwayXg = 0;

        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContext(style, "v29b-" + style + "-" + label + "-" + seed,
                    homeOvr, awayOvr, homeFormation, awayFormation);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);
            totalGoals += result.homeGoals() + result.awayGoals();
            totalHomeShots += result.homeShots();
            totalAwayShots += result.awayShots();
            totalHomeXg += result.homeXg();
            totalAwayXg += result.awayXg();
        }

        double avgTotal = (double) totalGoals / N_SIMULATIONS;
        double avgHomeShots = (double) totalHomeShots / N_SIMULATIONS;
        double avgAwayShots = (double) totalAwayShots / N_SIMULATIONS;

        System.out.printf("%-12s %-15s OVR %3dx%-3d: avg total=%.3f  (home shots=%.2f away shots=%.2f)%n",
                style, label, homeOvr, awayOvr, avgTotal, avgHomeShots, avgAwayShots);
    }

    private double runAndAverage(V24DetailedMatchEngine engine, TeamStyle style,
                                 int homeOvr, int awayOvr) {
        long totalGoals = 0;
        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContext(style, "v29b-summary-" + style + "-" + homeOvr + "x" + awayOvr + "-" + seed,
                    homeOvr, awayOvr, "4-3-3", "4-3-3");
            V24DetailedMatchResult result = engine.simulate(ctx, seed);
            totalGoals += result.homeGoals() + result.awayGoals();
        }
        return (double) totalGoals / N_SIMULATIONS;
    }

    private V24MatchContext buildContext(TeamStyle style, String matchId,
                                         int homeOvr, int awayOvr,
                                         String homeFormation, String awayFormation) {
        List<SessionPlayer> homeStart = makePlayersWithSkills("home", 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayersWithSkills("away", 11, awayOvr);
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new V24MatchContext(
                matchId,
                homeTeam.getSessionTeamId(), awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                homeFormation, awayFormation,
                style, style);
    }

    private List<SessionPlayer> makePlayersWithSkills(String prefix, int count, int ovr) {
        List<SessionPlayer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + "_p" + i;
            String position = positionForIndex(i);
            SessionPlayer p = SessionPlayer.custom(
                    id, 25, position,
                    ovr, ovr, ovr, ovr, ovr, ovr,
                    BigDecimal.valueOf(ovr * 1000));
            Map<PlayerSkill, Integer> skills = skillProfileForIndex(i);
            for (Map.Entry<PlayerSkill, Integer> e : skills.entrySet()) {
                p.setSkillLevel(e.getKey(), e.getValue());
            }
            Integer height = heightForIndex(i);
            if (height != null) {
                p.setHeightCm(height);
            }
            list.add(p);
        }
        return list;
    }

    private SessionTeam makeTeam(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, "4-3-3", null);
    }
}
