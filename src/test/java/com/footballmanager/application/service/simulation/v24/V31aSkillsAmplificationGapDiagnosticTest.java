package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V25D70-C31 Phase 1 (F0.1) — Skills amplification gap diagnostic (post-helper-fix).
 *
 * <p><b>Sprint C31 context:</b> C30 smoke FAIL — intermedios runtime avg 5.45 vs
 * target [3.0, 4.5]. Helper fix (C29) brought intermedios down from 6.5 → 5.45
 * (-16%) but did NOT close the gap. This test re-validates the skills
 * amplification hypothesis using the FIXED helper (per-position attrs scaled by
 * team_ovr/85) plus the LaLiga top-5 skills profile per C31 spec.
 *
 * <p><b>Goal of this test:</b> confirm whether the gap between diagnostic and
 * runtime is &gt;= 1.5x with skills ON. If yes → skills amplification is the
 * root cause and Phase 2 should reduce the skill multipliers. If no → the root
 * cause is a different layer (possessShare, shotLocation, etc.) and we pivot.
 *
 * <p><b>Helper fix applied (from C29 V29d):</b>
 * <ul>
 *   <li>Per-position attributes (GK attack=65, ATT attack=90, etc.) so the
 *       engine's strict-inequality key-attacker selection picks ATT, not GK.</li>
 *   <li>Scale attributes by team_ovr/85 so OVR=65 team is genuinely weaker.</li>
 * </ul>
 *
 * <p><b>Skills profile (C31 spec, LaLiga top-5 Real Madrid mix):</b>
 * <ul>
 *   <li>GK (index 0): WALL=92 (Courtois-style), height=199</li>
 *   <li>DEF index 1: MARKER=75 (1 strong marker), indices 2-4: no skills</li>
 *   <li>MID index 5: PASSER=85 (Valverde-style), indices 6-8: no skills</li>
 *   <li>ATT index 9: DRIBBLER=95, SHOOTER=78, SPEEDSTER=90 (Mbappé-like)</li>
 *   <li>ATT index 10: DRIBBLER=88, SHOOTER=90, SPEEDSTER=92 (Vinicius-like)</li>
 * </ul>
 *
 * <p><b>5 escenarios × BALANCED × N=200</b> = 1000 measurement matches.
 * The summary test runs intermedios A/B/C × BALANCED × N=200 = 600 matches
 * to compute the intermedios avg that goes into the gap calculation.
 *
 * <p><b>Gap target:</b> gap = runtime_intermedios_avg / diagnostic_intermedios_avg
 * (with skills ON). Runtime observed post-C29 = <b>5.45</b>. PASS if gap &gt;= 1.5x,
 * meaning diagnostic intermedios avg &lt;= 3.63.
 *
 * <p><b>Usage:</b>
 * <pre>
 *   mvn test -Dtest=V31aSkillsAmplificationGapDiagnosticTest -DfailIfNoTests=false
 * </pre>
 */
class V31aSkillsAmplificationGapDiagnosticTest {

    private static final int N_SIMULATIONS = 200;

    /**
     * Runtime observed in C30 smoke (post-C29 helper fix, REVISOR report).
     * Used as denominator for the runtime-vs-diagnostic gap ratio.
     */
    private static final double RUNTIME_INTERMEDIOS_AVG_POST_C29 = 5.45;

    /**
     * Per-position attribute profile (realistic football values).
     * Order: attack, defense, technique, speed, stamina, mentality.
     */
    private static int[] attributesForPosition(String position) {
        return switch (position) {
            case "GK" -> new int[]{65, 80, 65, 65, 70, 75};
            case "DEF" -> new int[]{70, 80, 72, 75, 80, 78};
            case "MID" -> new int[]{80, 72, 82, 78, 85, 82};
            case "ATT" -> new int[]{90, 55, 85, 90, 80, 82};
            default -> new int[]{75, 75, 75, 75, 75, 75};
        };
    }

    /**
     * C31 Phase 1 LaLiga top-5 skills profile per lineup index (0..10).
     * <p>NOTE: DEF index 1 uses MARKER=75 (vs V29d's 70) per C31 spec — slightly
     * stronger defensive pressure to mirror Real Madrid's actual roster shape.
     */
    private static Map<PlayerSkill, Integer> skillProfileForIndex(int index) {
        return switch (index) {
            case 0 -> Map.of(PlayerSkill.WALL, 92);
            case 1 -> Map.of(PlayerSkill.MARKER, 75);
            case 2, 3, 4 -> Map.of();
            case 5 -> Map.of(PlayerSkill.PASSER, 85);
            case 6, 7, 8 -> Map.of();
            case 9 -> Map.of(
                    PlayerSkill.DRIBBLER, 95,
                    PlayerSkill.SPEEDSTER, 90,
                    PlayerSkill.SHOOTER, 78);
            case 10 -> Map.of(
                    PlayerSkill.DRIBBLER, 88,
                    PlayerSkill.SHOOTER, 90,
                    PlayerSkill.SPEEDSTER, 92);
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
            case 9 -> 178;  // Mbappé
            case 10 -> 176; // Vinicius
            default -> null;
        };
    }

    // ========== Measurement tests: 5 escenarios × BALANCED × N=200 ==========

    @Test @DisplayName("V31a: PAREJOS 85x85 + LaLiga C31 profile + per-pos attrs + BALANCED")
    void balanced_parejos() {
        runScenario(TeamStyle.BALANCED, "PAREJOS", 85, 85);
    }

    @Test @DisplayName("V31a: INTERMEDIO-A 85x75 + LaLiga C31 profile + per-pos attrs + BALANCED")
    void balanced_intermedioA() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-A", 85, 75);
    }

    @Test @DisplayName("V31a: INTERMEDIO-B 85x70 + LaLiga C31 profile + per-pos attrs + BALANCED")
    void balanced_intermedioB() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-B", 85, 70);
    }

    @Test @DisplayName("V31a: INTERMEDIO-C 85x65 + LaLiga C31 profile + per-pos attrs + BALANCED")
    void balanced_intermedioC() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-C", 85, 65);
    }

    @Test @DisplayName("V31a: DESIGUALES 90x60 + LaLiga C31 profile + per-pos attrs + BALANCED")
    void balanced_desiguales() {
        runScenario(TeamStyle.BALANCED, "DESIGUALES", 90, 60);
    }

    // ========== Phase 1 (F0.1) confirmation assertion ==========

    /**
     * V25D70-C31 Phase 1 confirmation: computes intermedios diagnostic avg
     * (A/B/C × BALANCED × N=200) and compares to runtime post-C29 = 5.45.
     * <p>Pass condition: gap = runtime/diagnostic &gt;= 1.5x (i.e. diagnostic
     * intermedios avg &lt;= 3.63). If this fails, skills amplification is NOT
     * the dominant root cause — pivot to possessShare / shotLocation / etc.
     */
    @Test
    @DisplayName("V31a PHASE-1: intermedios diagnostic vs runtime gap must be >= 1.5x")
    void phase1_intermediosDiagnosticVsRuntime_gap() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D70-C31 PHASE-1 SKILLS AMPLIFICATION GAP DIAGNOSTIC");
        System.out.println("Runtime target (post-C29 REVISOR smoke): intermedios avg = "
                + RUNTIME_INTERMEDIOS_AVG_POST_C29);
        System.out.println("Gap target: runtime/diagnostic >= 1.5x");
        System.out.println("================================================================");

        double intermedioA = runAndAverage(engine, TeamStyle.BALANCED, 85, 75, "INT-A");
        double intermedioB = runAndAverage(engine, TeamStyle.BALANCED, 85, 70, "INT-B");
        double intermedioC = runAndAverage(engine, TeamStyle.BALANCED, 85, 65, "INT-C");
        double intermedioAvg = (intermedioA + intermedioB + intermedioC) / 3.0;

        // Diagnostic baselines (skills OFF, post-C28 midpoint-SQRT) for intermedios
        double baselineNoSkills = (1.93 + 2.50 + 3.29) / 3.0; // = 2.57

        double gapRuntimeVsDiagnostic = RUNTIME_INTERMEDIOS_AVG_POST_C29 / intermedioAvg;
        double amplificationVsBaseline = intermedioAvg / baselineNoSkills;

        System.out.println();
        System.out.println("PER-SCENARIO INTERMEDIOS DIAGNOSTIC (with skills ON, per-pos attrs)");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("  INTERMEDIO-A (85x75): %.3f  (baseline 1.93, amplification %.2fx)%n",
                intermedioA, intermedioA / 1.93);
        System.out.printf("  INTERMEDIO-B (85x70): %.3f  (baseline 2.50, amplification %.2fx)%n",
                intermedioB, intermedioB / 2.50);
        System.out.printf("  INTERMEDIO-C (85x65): %.3f  (baseline 3.29, amplification %.2fx)%n",
                intermedioC, intermedioC / 3.29);
        System.out.printf("  INTERMEDIOS AVG     : %.3f  (baseline %.3f, amplification %.2fx)%n",
                intermedioAvg, baselineNoSkills, amplificationVsBaseline);
        System.out.println();
        System.out.println("GAP ANALYSIS (runtime vs diagnostic with skills ON)");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("  Runtime intermedios avg (post-C29) = %.3f%n", RUNTIME_INTERMEDIOS_AVG_POST_C29);
        System.out.printf("  Diagnostic intermedios avg (skills ON) = %.3f%n", intermedioAvg);
        System.out.printf("  Gap runtime/diagnostic                = %.3fx%n", gapRuntimeVsDiagnostic);
        System.out.println();
        System.out.println("INTERPRETATION:");
        if (gapRuntimeVsDiagnostic >= 1.5) {
            System.out.println("  >>> PHASE-1 CONFIRMED: gap >= 1.5x. Skills amplification is the");
            System.out.println("      dominant root cause of the diagnostic-vs-runtime gap.");
            System.out.println("      Phase 2 should reduce skill multipliers (Option A recommended).");
        } else {
            System.out.println("  >>> PHASE-1 REJECTED: gap < 1.5x. Skills amplification is NOT the");
            System.out.println("      dominant root cause. Pivot to another layer:");
            System.out.println("      - possessShare (PASSER/MID distribution)");
            System.out.println("      - shotLocation (LONG_RANGE xG inflation)");
            System.out.println("      - other engine multipliers");
        }
        System.out.println("================================================================");

        assertTrue(gapRuntimeVsDiagnostic >= 1.5,
                "V25D70-C31 PHASE-1: gap runtime/diagnostic must be >= 1.5x. " +
                "Got gap=" + gapRuntimeVsDiagnostic +
                " (runtime=" + RUNTIME_INTERMEDIOS_AVG_POST_C29 +
                ", diagnostic=" + intermedioAvg + "). " +
                "If this fails, pivot to possessShare/shotLocation layer.");
    }

    // ========== Helpers ==========

    private void runScenario(TeamStyle style, String label, int homeOvr, int awayOvr) {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        long totalGoals = 0;
        long totalHomeShots = 0, totalAwayShots = 0;

        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContext(style, "v31a-" + style + "-" + label + "-" + seed,
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

    private double runAndAverage(V24DetailedMatchEngine engine, TeamStyle style,
                                 int homeOvr, int awayOvr, String label) {
        long totalGoals = 0;
        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContext(style, "v31a-phase1-" + label + "-" + seed,
                    homeOvr, awayOvr);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);
            totalGoals += result.homeGoals() + result.awayGoals();
        }
        double avg = (double) totalGoals / N_SIMULATIONS;
        System.out.printf("V31a phase1 %s: avg total = %.3f (N=%d)%n", label, avg, N_SIMULATIONS);
        return avg;
    }

    private V24MatchContext buildContext(TeamStyle style, String matchId, int homeOvr, int awayOvr) {
        List<SessionPlayer> homeStart = makePlayersWithPerPositionAttrs("home", 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayersWithPerPositionAttrs("away", 11, awayOvr);
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

    /**
     * Build 11 players with REALISTIC per-position attributes (NOT uniform),
     * SCALED by team_ovr / 85, plus the C31 LaLiga top-5 skills profile.
     */
    private List<SessionPlayer> makePlayersWithPerPositionAttrs(String prefix, int count, int teamOvr) {
        List<SessionPlayer> list = new ArrayList<>();
        double scale = teamOvr / 85.0;
        for (int i = 0; i < count; i++) {
            String id = prefix + "_p" + i;
            String position = positionForIndex(i);
            int[] baseAttrs = attributesForPosition(position);
            int[] attrs = new int[]{
                    Math.max(50, (int) Math.round(baseAttrs[0] * scale)),
                    Math.max(50, (int) Math.round(baseAttrs[1] * scale)),
                    Math.max(50, (int) Math.round(baseAttrs[2] * scale)),
                    Math.max(50, (int) Math.round(baseAttrs[3] * scale)),
                    Math.max(50, (int) Math.round(baseAttrs[4] * scale)),
                    Math.max(50, (int) Math.round(baseAttrs[5] * scale))
            };
            SessionPlayer p = SessionPlayer.custom(
                    id, 25, position,
                    attrs[0], attrs[1], attrs[2], attrs[3], attrs[4], attrs[5],
                    BigDecimal.valueOf(attrs[0] * 1000));
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
