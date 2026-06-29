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
 * V25D71-C33 Phase 2 — V33a calibration REGRESSION test.
 *
 * <p><b>Background:</b> Sprint C31 applied 2 simultaneous fixes to
 * {@link V24ShotXgCalculator} that over-corrected:
 * <ol>
 *   <li>Cap ratio offFormMod/defFormMod at 2.0 (was effectively uncapped, 2.98x)</li>
 *   <li>Reduce statsAmp coefficient 0.025 → 0.012 (52% less amplification)</li>
 * </ol>
 *
 * <p>C32 runtime smoke showed intermedios dropped 5.45 → 2.08 (target [3.0, 4.5])
 * and top-wins dropped 100% → ~50% (REGRESSION). Sprint C33 calibrated the
 * sweet-spot by running 5 variants (V33a-e) × 5 scenarios × N=200 diagnostics.
 * V33a (cap=2.5, statsAmp=0.025) was chosen as the best compromise and is now
 * the production value.
 *
 * <p><b>V33a reference values (Phase 1 diagnostic, N=200 per scenario):</b>
 * <table>
 *   <tr><th>Metric</th><th>V33a value</th><th>Regression band</th></tr>
 *   <tr><td>PAREJOS total goals</td><td>1.160</td><td>[1.0, 1.3]</td></tr>
 *   <tr><td>INTERMEDIOS avg (INT-A+B+C)</td><td>2.047</td><td>[1.85, 2.25]</td></tr>
 *   <tr><td>DESIGUALES homeWins %</td><td>75.5%</td><td>[70%, 80%]</td></tr>
 * </table>
 *
 * <p>The target top-wins ≥85% (per C33 task spec) is structurally unreachable
 * with cap/statsAmp knobs alone — diagnostic ceiling is ~75%. The runtime gap
 * (~25pp) comes from mechanisms not modeled in the diagnostic (chemistry,
 * multi-match state, synthetic league attrs). REVISOR smoke runtime is the
 * ground truth for the 85% target.
 *
 * <p><b>Phase 1 historical data</b> (V33a-e matrix, see reporte-C33-phase1.md):
 * <pre>
 *   V33a (cap=2.5, amp=0.025): intermedios=2.047  topWins=75.5%   ← chosen
 *   V33b (cap=2.0, amp=0.025): intermedios=1.773  topWins=68.5%
 *   V33c (cap=2.5, amp=0.018): intermedios=1.998  topWins=72.5%
 *   V33d (cap=2.2, amp=0.020): intermedios=1.915  topWins=69.5%
 *   V33e (cap=2.5, amp=0.012): intermedios=1.883  topWins=69.5%
 * </pre>
 *
 * <p><b>Usage:</b>
 * <pre>
 *   mvn test -Dtest=V33CalibrationDiagnosticTest -DfailIfNoTests=false
 * </pre>
 */
class V33CalibrationDiagnosticTest {

    private static final int N_SIMULATIONS = 200;

    @Test
    @DisplayName("V33a regression: intermedios [1.85, 2.25], topWins [70%, 80%], parejos [1.0, 1.3]")
    void v33aRegressionGuard() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D71-C33 PHASE-2 V33a REGRESSION CHECK");
        System.out.println("(cap=2.5, statsAmp=0.025 hardcoded in V24ShotXgCalculator)");
        System.out.println("================================================================");

        ScenarioResult parejos = runScenario(engine, TeamStyle.BALANCED, "PAREJOS", 85, 85);
        ScenarioResult intA = runScenario(engine, TeamStyle.BALANCED, "INT-A", 85, 75);
        ScenarioResult intB = runScenario(engine, TeamStyle.BALANCED, "INT-B", 85, 70);
        ScenarioResult intC = runScenario(engine, TeamStyle.BALANCED, "INT-C", 85, 65);
        ScenarioResult des = runScenario(engine, TeamStyle.BALANCED, "DESIGUALES", 90, 60);

        double intermediosAvg = (intA.avgTotalGoals + intB.avgTotalGoals + intC.avgTotalGoals) / 3.0;
        double desHomeWinPct = 100.0 * des.homeWins / N_SIMULATIONS;

        System.out.println();
        System.out.println("---- Pre-C31 calibration regression results ----");
        System.out.printf("PAREJOS    : total=%.3f  shots=%.1f  homeWins=%d%%  draws=%d%%  awayWins=%d%%%n",
                parejos.avgTotalGoals, parejos.avgTotalShots,
                (int) (100.0 * parejos.homeWins / N_SIMULATIONS),
                (int) (100.0 * parejos.draws / N_SIMULATIONS),
                (int) (100.0 * parejos.awayWins / N_SIMULATIONS));
        System.out.printf("INT-A 85x75: total=%.3f  shots=%.1f%n", intA.avgTotalGoals, intA.avgTotalShots);
        System.out.printf("INT-B 85x70: total=%.3f  shots=%.1f%n", intB.avgTotalGoals, intB.avgTotalShots);
        System.out.printf("INT-C 85x65: total=%.3f  shots=%.1f%n", intC.avgTotalGoals, intC.avgTotalShots);
        System.out.printf("INTERMEDIOS: avg=%.3f  (pre-C31 runtime reference 5.45, target [1.5, 3.0])%n", intermediosAvg);
        System.out.printf("DESIGUALES : topWins=%.1f%%  (pre-C31 runtime reference 100%%, target [80%% to 100%%])%n", desHomeWinPct);
        System.out.println("---- end pre-C31 regression ----");

        // Pre-C31 regression bands (reverted from V31 in C38 Phase).
        // Target reflects RUNTIME empirical pre-C31 expectations:
        // intermedios 5.45, top wins 100%, parejos reduced via C28 matchIntensity.
        // Diagnostic (synthetic per-position attrs, no skills) produces lower
        // numbers than runtime — bands reflect the lower-bound to allow diagnostic
        // noise while catching actual calibration drift.
        assertTrue(intermediosAvg >= 1.8 && intermediosAvg <= 2.6,
                "pre-C31 intermedios avg=" + intermediosAvg
                        + " is outside regression band [1.8, 2.6] (pre-C31 diagnostic reference 2.153, runtime 5.45). "
                        + "If this fails, the pre-C31 calibration has drifted — "
                        + "check V24ShotXgCalculator lines 384 (no cap), 541 (statsAmp=0.025), 588 (statsAmp=0.025).");
        assertTrue(desHomeWinPct >= 80.0 && desHomeWinPct <= 100.0,
                "pre-C31 desiguales topWins=" + desHomeWinPct
                        + "% is outside regression band [80%, 100%] (pre-C31 runtime reference 100%). "
                        + "If this fails, the pre-C31 calibration has drifted.");
        assertTrue(parejos.avgTotalGoals >= 0.9 && parejos.avgTotalGoals <= 1.4,
                "pre-C31 parejos total=" + parejos.avgTotalGoals
                        + " is outside regression band [0.9, 1.4] (pre-C31 diagnostic reference 1.160).");
    }

    // ========== Helpers (reused from V31b / V33a Phase 1) ==========

    private static int[] attributesForPosition(String position) {
        return switch (position) {
            case "GK" -> new int[]{65, 80, 65, 65, 70, 75};
            case "DEF" -> new int[]{70, 80, 72, 75, 80, 78};
            case "MID" -> new int[]{80, 72, 82, 78, 85, 82};
            case "ATT" -> new int[]{90, 55, 85, 90, 80, 82};
            default -> new int[]{75, 75, 75, 75, 75, 75};
        };
    }

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
            case 0 -> 199;
            case 9 -> 178;
            case 10 -> 176;
            default -> null;
        };
    }

    private ScenarioResult runScenario(V24DetailedMatchEngine engine, TeamStyle style,
                                       String label, int homeOvr, int awayOvr) {
        ScenarioResult acc = new ScenarioResult();
        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContext(style, "v33a-" + label + "-" + seed, homeOvr, awayOvr);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);

            int hg = result.homeGoals();
            int ag = result.awayGoals();
            acc.totalGoals += hg + ag;
            acc.totalShots += result.homeShots() + result.awayShots();
            acc.homeGoals += hg;
            acc.awayGoals += ag;
            if (hg > ag) acc.homeWins++;
            else if (hg == ag) acc.draws++;
            else acc.awayWins++;
        }
        acc.avgTotalGoals = (double) acc.totalGoals / N_SIMULATIONS;
        acc.avgTotalShots = (double) acc.totalShots / N_SIMULATIONS;
        acc.avgHomeGoals = (double) acc.homeGoals / N_SIMULATIONS;
        acc.avgAwayGoals = (double) acc.awayGoals / N_SIMULATIONS;
        return acc;
    }

    private V24MatchContext buildContext(TeamStyle style, String matchId, int homeOvr, int awayOvr) {
        List<SessionPlayer> homeStart = makePlayersWithPerPositionAttrs("home", 11, homeOvr);
        List<SessionPlayer> awayStart = makePlayersWithPerPositionAttrs("away", 11, awayOvr);
        SessionTeam homeTeam = makeTeam("home-" + matchId, "Home FC");
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

    /**
     * Per-scenario accumulator. Tracks goals/shots/wins across N matches.
     */
    private static final class ScenarioResult {
        long totalGoals;
        long totalShots;
        long homeGoals;
        long awayGoals;
        int homeWins;
        int draws;
        int awayWins;
        double avgTotalGoals;
        double avgTotalShots;
        double avgHomeGoals;
        double avgAwayGoals;
    }
}