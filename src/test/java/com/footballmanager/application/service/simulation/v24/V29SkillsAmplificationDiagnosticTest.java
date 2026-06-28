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
 * V25D69-C29 — Skills amplification diagnostic.
 *
 * <p>Sprint C29 task: reproduce the diagnostic-vs-runtime gap (2.45x measured
 * post-C28 in REVISOR smoke) by running the diagnostic WITH skills ON, so the
 * diagnostic mirrors the runtime path that REVISOR observed.
 *
 * <p>Comparison baseline (skills OFF, post-C28 midpoint-SQRT):
 * <ul>
 *   <li>PAREJOS (85x85, 0% diff): avg total = 1.50</li>
 *   <li>INTERMEDIO-A (85x75, 11.76% diff): avg total = 1.93</li>
 *   <li>INTERMEDIO-B (85x70, 17.65% diff): avg total = 2.50</li>
 *   <li>INTERMEDIO-C (85x65, 23.53% diff): avg total = 3.29</li>
 *   <li>DESIGUALES (90x60, 33.3% diff): avg total = 3.545</li>
 * </ul>
 *
 * <p>Runtime observed (REVISOR smoke, post-C28, skills active):
 * <ul>
 *   <li>PAREJOS: avg 1-2 goles (matches diagnostic)</li>
 *   <li>INTERMEDIOS: avg ~6.5 goles (vs diagnostic 2.57 avg → 2.53x gap)</li>
 *   <li>DESIGUALES: 0-5+ (similar to diagnostic)</li>
 * </ul>
 *
 * <p>This test mirrors {@link V27GoalBalanceBaselineDiagnosticTest} but injects
 * a realistic top-team skill profile per position:
 * <ul>
 *   <li>1 GK (position="GK") with WALL=85</li>
 *   <li>4 DEF (position="DEF") with MARKER=75, TACKLER=70</li>
 *   <li>4 MID (position="MID") with PASSER=80, DRIBBLER=70</li>
 *   <li>2 ATT (position="ATT") with DRIBBLER=90, SHOOTER=85, HEADER=70,
 *       AERIAL=70, heightCm=185 (enables HEADER compounding)</li>
 * </ul>
 *
 * <p>Phase 1 confirmation: if diagnostic with skills ON produces intermedios
 * closer to runtime (~6.5), the gap hypothesis is confirmed and the fix scope
 * is the skill multiplications in chanceProbability / xG.
 *
 * <p>Usage:
 * <pre>
 *   mvn test -Dtest=V29SkillsAmplificationDiagnosticTest
 * </pre>
 */
class V29SkillsAmplificationDiagnosticTest {

    private static final int N_SIMULATIONS = 200;

    // ========== Skills profile per position index (0..10) ==========

    /**
     * Returns the skill profile for a player at the given lineup index (0..10).
     * Layout (matches a 4-3-3 formation):
     * <ul>
     *   <li>index 0 = GK (position="GK"): WALL=85</li>
     *   <li>indices 1-4 = DEF (position="DEF"): MARKER=75, TACKLER=70</li>
     *   <li>indices 5-8 = MID (position="MID"): PASSER=80, DRIBBLER=70</li>
     *   <li>indices 9-10 = ATT (position="ATT"): DRIBBLER=90, SHOOTER=85,
     *       HEADER=70, AERIAL=70</li>
     * </ul>
     * The ATT players also get heightCm=185 to enable AERIAL compounding.
     */
    private static Map<PlayerSkill, Integer> skillProfileForIndex(int index) {
        return switch (index) {
            case 0 -> Map.of(PlayerSkill.WALL, 85);
            case 1, 2, 3, 4 -> Map.of(
                    PlayerSkill.MARKER, 75,
                    PlayerSkill.TACKLER, 70);
            case 5, 6, 7, 8 -> Map.of(
                    PlayerSkill.PASSER, 80,
                    PlayerSkill.DRIBBLER, 70);
            case 9, 10 -> Map.of(
                    PlayerSkill.DRIBBLER, 90,
                    PlayerSkill.SHOOTER, 85,
                    PlayerSkill.HEADER, 70,
                    PlayerSkill.AERIAL, 70);
            default -> Map.of();
        };
    }

    /**
     * Returns the position for the given lineup index (0..10).
     */
    private static String positionForIndex(int index) {
        if (index == 0) return "GK";
        if (index >= 1 && index <= 4) return "DEF";
        if (index >= 5 && index <= 8) return "MID";
        return "ATT";
    }

    /**
     * Returns the height for the given lineup index (only ATT get 185cm to
     * enable AERIAL compounding on headerMult).
     */
    private static Integer heightForIndex(int index) {
        return (index == 9 || index == 10) ? 185 : null;
    }

    // ========== PAREJOS scenario (OVR 85 × 85) ==========

    @Test
    @DisplayName("V25D69-C29 BASELINE: parejos (OVR 85×85) WITH skills ON")
    void measureBaseline_parejosOvr85x85_withSkills() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        runScenarioWithSkills(engine, "PAREJOS (OVR 85 × 85) WITH SKILLS ON",
                85, 85, "4-3-3", "4-3-3",
                /* expected baseline (skills OFF) */ 1.50);
    }

    // ========== INTERMEDIO scenarios (OVR 85 × 75/70/65) ==========

    @Test
    @DisplayName("V25D69-C29 BASELINE: INTERMEDIO-A (OVR 85×75) WITH skills ON")
    void measureBaseline_intermedioA_Ovr85x75_withSkills() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        runScenarioWithSkills(engine, "INTERMEDIO-A (OVR 85 × 75) WITH SKILLS ON",
                85, 75, "4-3-3", "4-3-3",
                /* expected baseline (skills OFF) */ 1.93);
    }

    @Test
    @DisplayName("V25D69-C29 BASELINE: INTERMEDIO-B (OVR 85×70) WITH skills ON")
    void measureBaseline_intermedioB_Ovr85x70_withSkills() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        runScenarioWithSkills(engine, "INTERMEDIO-B (OVR 85 × 70) WITH SKILLS ON",
                85, 70, "4-3-3", "4-3-3",
                /* expected baseline (skills OFF) */ 2.50);
    }

    @Test
    @DisplayName("V25D69-C29 BASELINE: INTERMEDIO-C (OVR 85×65) WITH skills ON")
    void measureBaseline_intermedioC_Ovr85x65_withSkills() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        runScenarioWithSkills(engine, "INTERMEDIO-C (OVR 85 × 65) WITH SKILLS ON",
                85, 65, "4-3-3", "4-3-3",
                /* expected baseline (skills OFF) */ 3.29);
    }

    // ========== DESIGUALES scenario (OVR 90 × 60) ==========

    @Test
    @DisplayName("V25D69-C29 BASELINE: DESIGUALES (OVR 90×60) WITH skills ON")
    void measureBaseline_desigualesOvr90x60_withSkills() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        runScenarioWithSkills(engine, "DESIGUALES (OVR 90 × 60) WITH SKILLS ON",
                90, 60, "4-3-3", "5-3-2",
                /* expected baseline (skills OFF) */ 3.545);
    }

    // ========== Phase 1 confirmation assertion ==========

    /**
     * V25D69-C29 — Phase 1 confirmation assertion.
     *
     * <p>The diagnostic WITH skills ON for INTERMEDIO-A must produce avg total
     * goals significantly higher than the baseline WITHOUT skills (1.93). If
     * the gap is &lt; 1.5x, the hypothesis (skills amplify chanceProb / xG)
     * is rejected and we look elsewhere. If the gap is ≥ 1.5x, the hypothesis
     * is confirmed and the F1 implementation must target skill amplification.
     *
     * <p>This assertion runs the diagnostic inline (no @Test method above is
     * reused) so we can capture avgTotal in a local variable and compare.
     */
    @Test
    @Disabled("V25D69-C29 research artifact — superseded by V29d (per-position attrs fix). Kept for documentation. ENABLE to re-run research.")
    @DisplayName("V25D69-C29 PHASE-1: intermedios with skills ON must show ≥1.5x amplification")
    void phase1_intermediosSkillsOnVsOff_amplificationConfirmed() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();

        // Compute avg total with skills ON for INTERMEDIO-A
        double avgWithSkillsA = runAndAverage(engine, 85, 75, "4-3-3", "4-3-3");
        double avgWithSkillsB = runAndAverage(engine, 85, 70, "4-3-3", "4-3-3");
        double avgWithSkillsC = runAndAverage(engine, 85, 65, "4-3-3", "4-3-3");
        double avgWithSkillsIntermediate = (avgWithSkillsA + avgWithSkillsB + avgWithSkillsC) / 3.0;

        // Baseline (skills OFF, post-C28) for intermedios:
        //   INTERMEDIO-A = 1.93, B = 2.50, C = 3.29 → avg 2.57
        double avgBaselineIntermediate = (1.93 + 2.50 + 3.29) / 3.0;

        double amplificationRatio = avgWithSkillsIntermediate / avgBaselineIntermediate;

        // Runtime post-C28 was 6.5 avg → 6.5 / 2.57 = 2.53x. We expect the
        // diagnostic with skills ON to be CLOSE TO the runtime value (6.5),
        // so the ratio diagnostic_with_skills / diagnostic_no_skills should be
        // around 2.0-3.0x. We assert ≥ 1.5x to confirm the hypothesis (skills
        // significantly amplify chanceProb / xG).
        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D69-C29 PHASE-1 CONFIRMATION");
        System.out.println("================================================================");
        System.out.printf("Diagnostic INTERMEDIOS WITH skills ON (avg of A/B/C) = %.3f%n", avgWithSkillsIntermediate);
        System.out.printf("Diagnostic INTERMEDIOS WITHOUT skills baseline        = %.3f%n", avgBaselineIntermediate);
        System.out.printf("Amplification ratio                                    = %.3fx%n", amplificationRatio);
        System.out.printf("  INTERMEDIO-A with skills: %.3f (baseline 1.93, ratio %.2fx)%n",
                avgWithSkillsA, avgWithSkillsA / 1.93);
        System.out.printf("  INTERMEDIO-B with skills: %.3f (baseline 2.50, ratio %.2fx)%n",
                avgWithSkillsB, avgWithSkillsB / 2.50);
        System.out.printf("  INTERMEDIO-C with skills: %.3f (baseline 3.29, ratio %.2fx)%n",
                avgWithSkillsC, avgWithSkillsC / 3.29);
        System.out.println();
        System.out.println("Runtime observed (REVISOR post-C28): intermedios avg ~6.5");
        System.out.printf("  Gap runtime vs diagnostic WITH skills    = %.3fx%n", 6.5 / avgWithSkillsIntermediate);
        System.out.printf("  Gap runtime vs diagnostic WITHOUT skills = %.3fx%n", 6.5 / avgBaselineIntermediate);
        System.out.println("================================================================");

        assertTrue(amplificationRatio >= 1.5,
                "V25D69-C29 PHASE-1: skills ON must amplify intermedios by ≥1.5x. " +
                "Got amplificationRatio=" + amplificationRatio +
                " (avgWithSkills=" + avgWithSkillsIntermediate +
                ", avgBaseline=" + avgBaselineIntermediate + ")");
    }

    // ========== Helpers ==========

    /**
     * Runs N=200 matches for the given scenario WITH skills ON and prints a
     * histogram with the avg total goals + a comparison to the baseline.
     */
    private void runScenarioWithSkills(V24DetailedMatchEngine engine, String title,
                                       int homeOvr, int awayOvr,
                                       String homeFormation, String awayFormation,
                                       double baselineAvgTotal) {
        int[] homeGoalsHist = new int[10];
        int[] awayGoalsHist = new int[10];
        long totalGoals = 0;
        long totalHomeShots = 0;
        long totalAwayShots = 0;
        double totalHomeXg = 0;
        double totalAwayXg = 0;
        long homeWins = 0, draws = 0, awayWins = 0;

        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContextWithSkills(
                    "skills-" + title.replaceAll("\\W+", "-") + "-" + seed,
                    homeOvr, awayOvr, homeFormation, awayFormation);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);

            int hg = Math.min(9, result.homeGoals());
            int ag = Math.min(9, result.awayGoals());
            homeGoalsHist[hg]++;
            awayGoalsHist[ag]++;
            totalGoals += result.homeGoals() + result.awayGoals();
            totalHomeShots += result.homeShots();
            totalAwayShots += result.awayShots();
            totalHomeXg += result.homeXg();
            totalAwayXg += result.awayXg();
            if (result.homeGoals() > result.awayGoals()) homeWins++;
            else if (result.homeGoals() == result.awayGoals()) draws++;
            else awayWins++;
        }

        double avgTotal = (double) totalGoals / N_SIMULATIONS;
        double amplificationRatio = avgTotal / baselineAvgTotal;
        double avgHomeShots = (double) totalHomeShots / N_SIMULATIONS;
        double avgAwayShots = (double) totalAwayShots / N_SIMULATIONS;
        double avgHomeXg = totalHomeXg / N_SIMULATIONS;
        double avgAwayXg = totalAwayXg / N_SIMULATIONS;

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D69-C29 SKILLS DIAGNOSTIC — " + title);
        System.out.println("N=" + N_SIMULATIONS + " seeds (1..N)");
        System.out.println("================================================================");
        System.out.printf("avg total goals (skills ON)  = %.3f%n", avgTotal);
        System.out.printf("avg total goals (skills OFF) = %.3f  (baseline from V27 diagnostic post-C28)%n", baselineAvgTotal);
        System.out.printf("AMPLIFICATION RATIO          = %.3fx%n", amplificationRatio);
        System.out.println();
        System.out.printf("avg home shots = %.2f, avg away shots = %.2f%n", avgHomeShots, avgAwayShots);
        System.out.printf("avg home xG    = %.3f, avg away xG    = %.3f%n", avgHomeXg, avgAwayXg);
        System.out.println();
        System.out.println("PER-TEAM GOAL DISTRIBUTION (with skills)");
        System.out.println("----------------------------------------------------------------");
        for (int g = 0; g <= 6; g++) {
            int h = g < homeGoalsHist.length ? homeGoalsHist[g] : 0;
            int a = g < awayGoalsHist.length ? awayGoalsHist[g] : 0;
            System.out.printf("  P(goals=%d)  home=%6.2f%%  away=%6.2f%%%n", g,
                    100.0 * h / N_SIMULATIONS, 100.0 * a / N_SIMULATIONS);
        }
        int h3plus = 0, a3plus = 0;
        for (int g = 3; g < homeGoalsHist.length; g++) h3plus += homeGoalsHist[g];
        for (int g = 3; g < awayGoalsHist.length; g++) a3plus += awayGoalsHist[g];
        System.out.printf("  P(goals>=3) home=%6.2f%%  away=%6.2f%%%n",
                100.0 * h3plus / N_SIMULATIONS, 100.0 * a3plus / N_SIMULATIONS);
        System.out.println();
        System.out.println("RESULT DISTRIBUTION (W/D/L for HOME)");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("  HOME wins      = %6.2f%%%n", 100.0 * homeWins / N_SIMULATIONS);
        System.out.printf("  DRAWS          = %6.2f%%%n", 100.0 * draws / N_SIMULATIONS);
        System.out.printf("  AWAY wins      = %6.2f%%%n", 100.0 * awayWins / N_SIMULATIONS);
        System.out.println("================================================================");
    }

    /**
     * Runs N=200 matches and returns the avg total goals. Used by the
     * Phase 1 confirmation assertion to capture the value for comparison.
     */
    private double runAndAverage(V24DetailedMatchEngine engine,
                                 int homeOvr, int awayOvr,
                                 String homeFormation, String awayFormation) {
        long totalGoals = 0;
        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContextWithSkills(
                    "phase1-" + homeOvr + "x" + awayOvr + "-" + seed,
                    homeOvr, awayOvr, homeFormation, awayFormation);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);
            totalGoals += result.homeGoals() + result.awayGoals();
        }
        return (double) totalGoals / N_SIMULATIONS;
    }

    private V24MatchContext buildContextWithSkills(String matchId, int homeOvr, int awayOvr,
                                                   String homeFormation, String awayFormation) {
        List<SessionPlayer> homeStart = makePlayersWithSkills("home", 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayersWithSkills("away", 11, awayOvr);
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new V24MatchContext(
                matchId,
                homeTeam.getSessionTeamId(),
                awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                homeFormation, awayFormation,
                TeamStyle.BALANCED, TeamStyle.BALANCED
        );
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
            // Inject skill profile for this position index
            Map<PlayerSkill, Integer> skills = skillProfileForIndex(i);
            for (Map.Entry<PlayerSkill, Integer> e : skills.entrySet()) {
                p.setSkillLevel(e.getKey(), e.getValue());
            }
            // Inject height for ATT (needed for AERIAL compounding)
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
