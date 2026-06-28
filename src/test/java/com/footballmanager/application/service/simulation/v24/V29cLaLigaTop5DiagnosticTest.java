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
 * V25D69-C29 Phase 1c: Per-player variance (LaLiga top-5 asymmetric profile).
 *
 * <p>After Phase 1 (skills uniform = reduce) and Phase 1b (style = insufficient
 * 1.43x amplification) failed to reproduce the runtime gap (6.5 vs 2.57 baseline
 * = 2.5x), this test uses the realistic LaLiga top-5 curated skills profile
 * where only 2-3 attackers carry strong offensive skills and the rest of the
 * team has NO skills (or weak ones). This asymmetry is the missing factor:
 *
 * <ul>
 *   <li>Real Madrid smoke: 1 GK (Courtois WALL=92), 4 DEF (most with NO
 *       defensive skills), 4 MID (1-2 with PASSER only), 2 ATT (Mbappé +
 *       Vinicius with DRIBBLER=88-95 + SHOOTER=78-90 + SPEEDSTER=90-92).</li>
 *   <li>Defensive reductions (MARKER -25%, TACKLER -28%, WALL/1.92) apply
 *       ONLY when the opponent has these skills. If only 1 player per team
 *       has WALL, the reduction is limited to shots against that player.</li>
 *   <li>Offensive amplification (DRIBBLER +30%, SPEEDSTER +33% in COUNTER)
 *       applies on EVERY shot by the key attacker.</li>
 *   <li>Net: offensive amplification wins because the key attacker is
 *       ALWAYS a superstar, but defensive skills are distributed thinly.</li>
 * </ul>
 *
 * <p>15 measurements: 5 scenarios x 3 styles, with LaLiga-realistic profile.
 *
 * <p>Usage: mvn test -Dtest=V29cLaLigaTop5DiagnosticTest -DfailIfNoTests=false
 */
class V29cLaLigaTop5DiagnosticTest {

    private static final int N_SIMULATIONS = 200;

    /**
     * Realistic Real Madrid top-5 profile (from LaLigaSeedService V25D32):
     * <ul>
     *   <li>index 0 = GK (Courtois): WALL=92, height=199</li>
     *   <li>indices 1-4 = DEF: 1 with MARKER=70, 3 with NO skills</li>
     *   <li>indices 5-8 = MID: 1 with PASSER=85 (Valverde-like), 3 with NO skills</li>
     *   <li>indices 9-10 = ATT: DRIBBLER=95 + SPEEDSTER=90 + SHOOTER=85 (Vinicius+Mbappé-like)</li>
     * </ul>
     */
    private static Map<PlayerSkill, Integer> skillProfileForIndex(int index) {
        return switch (index) {
            case 0 -> Map.of(PlayerSkill.WALL, 92);
            case 1 -> Map.of(PlayerSkill.MARKER, 70);
            case 2, 3, 4 -> Map.of();
            case 5 -> Map.of(PlayerSkill.PASSER, 85);
            case 6, 7, 8 -> Map.of();
            case 9 -> Map.of(PlayerSkill.DRIBBLER, 95, PlayerSkill.SPEEDSTER, 90, PlayerSkill.SHOOTER, 78);
            case 10 -> Map.of(PlayerSkill.DRIBBLER, 88, PlayerSkill.SHOOTER, 90, PlayerSkill.SPEEDSTER, 92);
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
        return switch (index) {
            case 0 -> 199;  // Courtois
            case 9 -> 176;  // Vinicius
            case 10 -> 178; // Mbappé
            default -> null;
        };
    }

    // ========== ATTACKING style with realistic profile ==========

    @Test @DisplayName("V29c: PAREJOS 85x85 + LaLiga profile + ATTACKING")
    void attacking_parejos() {
        runScenario(TeamStyle.ATTACKING, "PAREJOS", 85, 85);
    }

    @Test @DisplayName("V29c: INTERMEDIO-A 85x75 + LaLiga profile + ATTACKING")
    void attacking_intermedioA() {
        runScenario(TeamStyle.ATTACKING, "INTERMEDIO-A", 85, 75);
    }

    @Test @DisplayName("V29c: INTERMEDIO-B 85x70 + LaLiga profile + ATTACKING")
    void attacking_intermedioB() {
        runScenario(TeamStyle.ATTACKING, "INTERMEDIO-B", 85, 70);
    }

    @Test @DisplayName("V29c: INTERMEDIO-C 85x65 + LaLiga profile + ATTACKING")
    void attacking_intermedioC() {
        runScenario(TeamStyle.ATTACKING, "INTERMEDIO-C", 85, 65);
    }

    @Test @DisplayName("V29c: DESIGUALES 90x60 + LaLiga profile + ATTACKING")
    void attacking_desiguales() {
        runScenario(TeamStyle.ATTACKING, "DESIGUALES", 90, 60);
    }

    // ========== BALANCED style (baseline for comparison) ==========

    @Test @DisplayName("V29c: PAREJOS 85x85 + LaLiga profile + BALANCED")
    void balanced_parejos() {
        runScenario(TeamStyle.BALANCED, "PAREJOS", 85, 85);
    }

    @Test @DisplayName("V29c: INTERMEDIO-A 85x75 + LaLiga profile + BALANCED")
    void balanced_intermedioA() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-A", 85, 75);
    }

    @Test @DisplayName("V29c: INTERMEDIO-B 85x70 + LaLiga profile + BALANCED")
    void balanced_intermedioB() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-B", 85, 70);
    }

    @Test @DisplayName("V29c: INTERMEDIO-C 85x65 + LaLiga profile + BALANCED")
    void balanced_intermedioC() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-C", 85, 65);
    }

    @Test @DisplayName("V29c: DESIGUALES 90x60 + LaLiga profile + BALANCED")
    void balanced_desiguales() {
        runScenario(TeamStyle.BALANCED, "DESIGUALES", 90, 60);
    }

    // ========== COUNTER style (test SPEEDSTER bonus) ==========

    @Test @DisplayName("V29c: PAREJOS 85x85 + LaLiga profile + COUNTER")
    void counter_parejos() {
        runScenario(TeamStyle.COUNTER, "PAREJOS", 85, 85);
    }

    @Test @DisplayName("V29c: INTERMEDIO-A 85x75 + LaLiga profile + COUNTER")
    void counter_intermedioA() {
        runScenario(TeamStyle.COUNTER, "INTERMEDIO-A", 85, 75);
    }

    @Test @DisplayName("V29c: INTERMEDIO-B 85x70 + LaLiga profile + COUNTER")
    void counter_intermedioB() {
        runScenario(TeamStyle.COUNTER, "INTERMEDIO-B", 85, 70);
    }

    @Test @DisplayName("V29c: INTERMEDIO-C 85x65 + LaLiga profile + COUNTER")
    void counter_intermedioC() {
        runScenario(TeamStyle.COUNTER, "INTERMEDIO-C", 85, 65);
    }

    @Test @DisplayName("V29c: DESIGUALES 90x60 + LaLiga profile + COUNTER")
    void counter_desiguales() {
        runScenario(TeamStyle.COUNTER, "DESIGUALES", 90, 60);
    }

    // ========== Phase 1c summary ==========

    @Test
    @Disabled("V25D69-C29 research artifact — superseded by V29d. ENABLE to re-run.")
    @DisplayName("V29c PHASE-1c SUMMARY: LaLiga profile must amplify intermedios")
    void phase1c_summary() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D69-C29 PHASE-1c LaLiga TOP-5 ASYMMETRIC PROFILE");
        System.out.println("================================================================");

        String[] labels = {"INTERMEDIO-A 85x75", "INTERMEDIO-B 85x70", "INTERMEDIO-C 85x65"};
        double balSum = 0, attSum = 0, couSum = 0;

        for (int i = 0; i < 3; i++) {
            int homeOvr = 85;
            int awayOvr = (i == 0) ? 75 : (i == 1) ? 70 : 65;
            String label = labels[i];
            double bal = runAndAverage(engine, TeamStyle.BALANCED, homeOvr, awayOvr);
            double att = runAndAverage(engine, TeamStyle.ATTACKING, homeOvr, awayOvr);
            double cou = runAndAverage(engine, TeamStyle.COUNTER, homeOvr, awayOvr);
            balSum += bal;
            attSum += att;
            couSum += cou;
            System.out.printf("%s: BALANCED=%.3f  ATTACKING=%.3f  COUNTER=%.3f%n",
                    label, bal, att, cou);
        }

        double balAvg = balSum / 3.0;
        double attAvg = attSum / 3.0;
        double couAvg = couSum / 3.0;
        double baselineNoSkills = 2.57;

        System.out.println();
        System.out.println("INTERMEDIOS AVG (A+B+C) with LaLiga asymmetric profile");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("BALANCED  :  %.3f  (x%.2f vs no-skills baseline %.2f)%n", balAvg, balAvg / baselineNoSkills, baselineNoSkills);
        System.out.printf("ATTACKING :  %.3f  (x%.2f vs no-skills baseline; x%.2f vs BALANCED)%n",
                attAvg, attAvg / baselineNoSkills, attAvg / balAvg);
        System.out.printf("COUNTER   :  %.3f  (x%.2f vs no-skills baseline; x%.2f vs BALANCED)%n",
                couAvg, couAvg / baselineNoSkills, couAvg / balAvg);
        System.out.println();
        System.out.println("Runtime observed (REVISOR post-C28): intermedios avg ~6.5");
        System.out.printf("  ATTACKING gap runtime vs diagnostic-with-skills: %.3fx%n", 6.5 / attAvg);
        System.out.printf("  COUNTER   gap runtime vs diagnostic-with-skills: %.3fx%n", 6.5 / couAvg);
        System.out.printf("  BALANCED  gap runtime vs diagnostic-with-skills: %.3fx%n", 6.5 / balAvg);
        System.out.println("================================================================");

        // Phase 1c confirmation: at least one of ATTACKING/COUNTER must
        // amplify intermedios to >=2.0x of the no-skills baseline (2.57).
        // Runtime gap target: 6.5/2.57 = 2.53x. We accept the gap if we
        // can amplify to within 1.5x of runtime (= >=4.3).
        assertTrue(attAvg >= 2.0 * baselineNoSkills || couAvg >= 2.0 * baselineNoSkills,
                "V25D69-C29 PHASE-1c: at least one style must amplify intermedios to >=2x of no-skills baseline (5.14). " +
                "Got attRaw=" + attAvg + ", couRaw=" + couAvg + " (baseline=" + baselineNoSkills + ")");
    }

    // ========== Helpers ==========

    private void runScenario(TeamStyle style, String label, int homeOvr, int awayOvr) {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        long totalGoals = 0;
        long totalHomeShots = 0, totalAwayShots = 0;

        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContext(style, "v29c-" + style + "-" + label + "-" + seed,
                    homeOvr, awayOvr);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);
            totalGoals += result.homeGoals() + result.awayGoals();
            totalHomeShots += result.homeShots();
            totalAwayShots += result.awayShots();
        }

        double avgTotal = (double) totalGoals / N_SIMULATIONS;
        double avgHomeShots = (double) totalHomeShots / N_SIMULATIONS;
        double avgAwayShots = (double) totalAwayShots / N_SIMULATIONS;
        System.out.printf("%-12s %-15s OVR %3dx%-3d: avg total=%.3f  (home shots=%.2f away shots=%.2f)%n",
                style, label, homeOvr, awayOvr, avgTotal, avgHomeShots, avgAwayShots);
    }

    private double runAndAverage(V24DetailedMatchEngine engine, TeamStyle style, int homeOvr, int awayOvr) {
        long totalGoals = 0;
        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContext(style, "v29c-summary-" + style + "-" + homeOvr + "x" + awayOvr + "-" + seed,
                    homeOvr, awayOvr);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);
            totalGoals += result.homeGoals() + result.awayGoals();
        }
        return (double) totalGoals / N_SIMULATIONS;
    }

    private V24MatchContext buildContext(TeamStyle style, String matchId, int homeOvr, int awayOvr) {
        List<SessionPlayer> homeStart = makePlayersWithLaLigaProfile("home", 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayersWithLaLigaProfile("away", 11, awayOvr);
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Real Madrid");
        SessionTeam awayTeam = makeTeam("away-" + matchId, "Away FC");
        return new V24MatchContext(
                matchId,
                homeTeam.getSessionTeamId(), awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                style, style);
    }

    private List<SessionPlayer> makePlayersWithLaLigaProfile(String prefix, int count, int ovr) {
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
