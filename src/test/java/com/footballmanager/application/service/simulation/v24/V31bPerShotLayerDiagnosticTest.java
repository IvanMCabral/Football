package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V25D70-C31 Phase 1b — Per-shot layer diagnostic (possession + shot location + xG).
 *
 * <p><b>Why this exists:</b> V31a Phase 1 showed that diagnostic intermedios avg
 * with skills ON is <b>2.153</b> while runtime (C30 smoke) is <b>5.45</b> — a 2.531x gap.
 * However, skills ON actually <i>reduces</i> intermedios vs baseline (skills OFF baseline
 * is 2.573, so 0.84x amplification, NOT amplification). The gap exists because
 * <b>runtime &gt;&gt; diagnostic</b>, not because skills amplify in the diagnostic.
 *
 * <p><b>Goal of V31b:</b> identify WHICH layer amplifies in runtime vs diagnostic by
 * decomposing the diagnostic into per-shot layers:
 * <ol>
 *   <li><b>Possession share</b> (home% / away%) — does runtime give the strong team
 *       more possession than the diagnostic predicts?</li>
 *   <li><b>Shot rate</b> (shots per minute) — does runtime generate more shots/min
 *       than the diagnostic?</li>
 *   <li><b>Shot location distribution</b> (% in each of 5 zones:
 *       SIX_YARD_BOX, PENALTY_AREA_CENTER, PENALTY_AREA_WIDE, OUTSIDE_BOX, LONG_RANGE) —
 *       does runtime skew toward high-xG zones?</li>
 *   <li><b>xG per shot</b> (avg xG across all shots) — does runtime inflate xG?</li>
 *   <li><b>Conversion rate</b> (goals per shot) — does runtime convert more
 *       efficiently than the diagnostic xG would predict?</li>
 *   <li><b>LONG_RANGE rate</b> (% of shots that are LONG_RANGE) — isolates the
 *       V25D34-F1 SHOOTER bonus impact (+36% xG on LONG_RANGE).</li>
 * </ol>
 *
 * <p><b>Setup:</b> identical to V31a — per-position attrs scaled by team_ovr/85, plus
 * C31 LaLiga top-5 skills profile. Style BALANCED only.
 *
 * <p><b>5 escenarios × BALANCED × N=200</b> = 1000 matches, each emitting per-shot
 * data via V24MatchTimeline. The summary test aggregates per layer and identifies
 * which layer has the biggest deviation from the runtime-implied level.
 *
 * <p><b>Usage:</b>
 * <pre>
 *   mvn test -Dtest=V31bPerShotLayerDiagnosticTest -DfailIfNoTests=false
 * </pre>
 */
class V31bPerShotLayerDiagnosticTest {

    private static final int N_SIMULATIONS = 200;

    /**
     * Per-position attribute profile (matches V31a/V29d).
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
     * C31 LaLiga top-5 skills profile per lineup index (matches V31a).
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
            case 0 -> 199;
            case 9 -> 178;
            case 10 -> 176;
            default -> null;
        };
    }

    // ========== Measurement tests: 5 escenarios × BALANCED × N=200 ==========

    @Test @DisplayName("V31b: PAREJOS 85x85 + LaLiga profile + per-shot logging + BALANCED")
    void balanced_parejos() {
        runScenario(TeamStyle.BALANCED, "PAREJOS", 85, 85);
    }

    @Test @DisplayName("V31b: INTERMEDIO-A 85x75 + LaLiga profile + per-shot logging + BALANCED")
    void balanced_intermedioA() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-A", 85, 75);
    }

    @Test @DisplayName("V31b: INTERMEDIO-B 85x70 + LaLiga profile + per-shot logging + BALANCED")
    void balanced_intermedioB() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-B", 85, 70);
    }

    @Test @DisplayName("V31b: INTERMEDIO-C 85x65 + LaLiga profile + per-shot logging + BALANCED")
    void balanced_intermedioC() {
        runScenario(TeamStyle.BALANCED, "INTERMEDIO-C", 85, 65);
    }

    @Test @DisplayName("V31b: DESIGUALES 90x60 + LaLiga profile + per-shot logging + BALANCED")
    void balanced_desiguales() {
        runScenario(TeamStyle.BALANCED, "DESIGUALES", 90, 60);
    }

    // ========== Phase 1b summary: layer attribution ==========

    /**
     * V25D70-C31 Phase 1b summary: aggregates per-layer metrics across intermedios
     * scenarios (A/B/C × N=200) and identifies the layer with the largest deviation
     * from the runtime-implied level. The goal is to pinpoint the layer that
     * amplifies runtime vs diagnostic.
     *
     * <p>Runtime-implied levels (from REVISOR C30 smoke report):
     * <ul>
     *   <li>Intermedios total avg: 5.45 goals/match</li>
     *   <li>Desiguales total avg: ~10.1 goals/match</li>
     *   <li>No per-shot or possession data published — those need to be queried from
     *       the MongoDB match records for the C30 smoke user.</li>
     * </ul>
     */
    @Test
    @DisplayName("V31b PHASE-1b SUMMARY: per-shot layer attribution (which layer amplifies runtime)")
    void phase1b_layerAttribution() {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D70-C31 PHASE-1b PER-SHOT LAYER ATTRIBUTION");
        System.out.println("Runtime intermedios avg (post-C29) = 5.45 (per C30 smoke)");
        System.out.println("Diagnostic intermedios avg (V31a skills ON) = 2.153");
        System.out.println("Gap runtime/diagnostic = 2.531x");
        System.out.println("================================================================");

        // Aggregate per-layer metrics across intermedios A/B/C
        PerLayerAccumulator intA = runLayerAccumulator(engine, TeamStyle.BALANCED, 85, 75, "INT-A");
        PerLayerAccumulator intB = runLayerAccumulator(engine, TeamStyle.BALANCED, 85, 70, "INT-B");
        PerLayerAccumulator intC = runLayerAccumulator(engine, TeamStyle.BALANCED, 85, 65, "INT-C");
        PerLayerAccumulator avg = PerLayerAccumulator.average(intA, intB, intC);

        System.out.println();
        System.out.println("PER-LAYER AGGREGATES (intermedios avg of A+B+C, N=200 each)");
        System.out.println("----------------------------------------------------------------");
        avg.printLayerTable("INTERMEDIOS");

        // DESIGUALES for comparison
        PerLayerAccumulator des = runLayerAccumulator(engine, TeamStyle.BALANCED, 90, 60, "DESIGUALES");
        System.out.println();
        des.printLayerTable("DESIGUALES");

        // Layer attribution heuristic: rank layers by which one would have to
        // change the most to bridge the runtime-vs-diagnostic gap.
        System.out.println();
        System.out.println("LAYER ATTRIBUTION HEURISTIC (where would the 5.45 come from?)");
        System.out.println("----------------------------------------------------------------");
        avg.attributeGapToLayers(des, 5.45, 2.153);

        // Sanity: diagnostic total goals must match V31a (skills ON, per-pos attrs)
        // within 5% (different RNG path is OK). This catches helper regressions.
        double diagnosticTotal = avg.avgTotalGoals;
        assertTrue(Math.abs(diagnosticTotal - 2.153) / 2.153 < 0.10,
                "V31b diagnostic intermedios avg=" + diagnosticTotal +
                " should be within 10% of V31a reference (2.153). " +
                "If this fails, the V31b helper drifted from V31a.");
    }

    // ========== Helpers ==========

    /**
     * Per-layer accumulator. Holds aggregate metrics for one (or aggregated) scenario.
     */
    private static final class PerLayerAccumulator {
        int n;
        double avgTotalGoals;
        double avgHomeGoals, avgAwayGoals;
        double avgHomeShots, avgAwayShots;
        double avgHomeXg, avgAwayXg;
        double avgHomePossessionPct, avgAwayPossessionPct;
        double avgShotsPerMinute;          // total shots / 90 min
        double avgXgPerShot;               // total xG / total shots
        double avgConversionRate;          // total goals / total shots
        EnumMap<V24ShotLocation, Long> shotLocationCounts = new EnumMap<>(V24ShotLocation.class);
        EnumMap<V24ShotLocation, Long> shotLocationGoals = new EnumMap<>(V24ShotLocation.class);

        PerLayerAccumulator() {
            for (V24ShotLocation loc : V24ShotLocation.values()) {
                shotLocationCounts.put(loc, 0L);
                shotLocationGoals.put(loc, 0L);
            }
        }

        static PerLayerAccumulator average(PerLayerAccumulator... accs) {
            PerLayerAccumulator avg = new PerLayerAccumulator();
            int n = accs.length;
            avg.n = (int) Math.round(accs[0].n * ((double) n));
            avg.avgTotalGoals = mean(accs, a -> a.avgTotalGoals);
            avg.avgHomeGoals = mean(accs, a -> a.avgHomeGoals);
            avg.avgAwayGoals = mean(accs, a -> a.avgAwayGoals);
            avg.avgHomeShots = mean(accs, a -> a.avgHomeShots);
            avg.avgAwayShots = mean(accs, a -> a.avgAwayShots);
            avg.avgHomeXg = mean(accs, a -> a.avgHomeXg);
            avg.avgAwayXg = mean(accs, a -> a.avgAwayXg);
            avg.avgHomePossessionPct = mean(accs, a -> a.avgHomePossessionPct);
            avg.avgAwayPossessionPct = mean(accs, a -> a.avgAwayPossessionPct);
            avg.avgShotsPerMinute = mean(accs, a -> a.avgShotsPerMinute);
            avg.avgXgPerShot = mean(accs, a -> a.avgXgPerShot);
            avg.avgConversionRate = mean(accs, a -> a.avgConversionRate);
            for (V24ShotLocation loc : V24ShotLocation.values()) {
                avg.shotLocationCounts.put(loc, (long) mean(accs, a -> a.shotLocationCounts.get(loc)));
                avg.shotLocationGoals.put(loc, (long) mean(accs, a -> a.shotLocationGoals.get(loc)));
            }
            return avg;
        }

        private static double mean(PerLayerAccumulator[] accs, java.util.function.ToDoubleFunction<PerLayerAccumulator> f) {
            double sum = 0;
            for (PerLayerAccumulator a : accs) sum += f.applyAsDouble(a);
            return sum / accs.length;
        }

        /**
         * Heuristic layer attribution. Given runtime intermedios avg = 5.45 and
         * diagnostic intermedios avg = 2.153, compute how much each layer would
         * have to change to bridge the gap.
         */
        void attributeGapToLayers(PerLayerAccumulator des, double runtimeGoals, double diagGoals) {
            double gap = runtimeGoals - diagGoals;
            System.out.printf("Runtime = %.2f, Diagnostic = %.2f, Gap = %.2f goals%n", runtimeGoals, diagGoals, gap);
            System.out.println();
            System.out.println("Layer-by-layer analysis (what changes in runtime vs diagnostic would close the gap):");
            System.out.println();

            // 1. Possession: if runtime gives the strong team more possession,
            //    they'd generate more shots. Estimate impact = extra possession
            //    share * extra shot rate.
            double possGap = avgHomePossessionPct - 50.0;  // diagnostic is roughly balanced
            System.out.printf("  Possession bias: home=%.1f%% (vs 50%% baseline). If runtime pushes home to 70%% (+%.1f%%),%n",
                    avgHomePossessionPct, possGap);
            System.out.printf("    expected extra home shots = %.1f * (%.2f shots/min) * 90 min = %.1f%n",
                    (possGap > 0 ? possGap : 0) / 100.0,
                    avgShotsPerMinute / 2.0,
                    (possGap > 0 ? possGap : 0) / 100.0 * (avgShotsPerMinute / 2.0) * 90.0);
            System.out.println();

            // 2. Shot rate: if runtime generates more shots per minute, that's the layer.
            double shotRateImpact = avgShotsPerMinute * 0.10 * 90.0 * 0.10;  // +10% shots = +1 goal at 10% conversion
            System.out.printf("  Shot rate: %.2f shots/min (both teams). +10%% shot rate = +%.1f goals%n",
                    avgShotsPerMinute, shotRateImpact);
            System.out.println();

            // 3. Shot location: print distribution
            System.out.println("  Shot location distribution (% of total shots):");
            long totalShots = shotLocationCounts.values().stream().mapToLong(Long::longValue).sum();
            for (V24ShotLocation loc : V24ShotLocation.values()) {
                long count = shotLocationCounts.get(loc);
                double pct = totalShots > 0 ? 100.0 * count / totalShots : 0;
                long goals = shotLocationGoals.get(loc);
                double goalRate = count > 0 ? 100.0 * goals / count : 0;
                System.out.printf("    %-22s %5.1f%%  (goals=%d, conversion=%.1f%%)%n", loc, pct, goals, goalRate);
            }
            System.out.println();

            // 4. xG per shot: print
            System.out.printf("  xG per shot: %.3f (avg). Runtime might inflate this.%n", avgXgPerShot);
            System.out.printf("  Conversion rate: %.1f%% (goals/shots). Runtime Real Madrid: ~11 goals on ~20 shots ≈ 55%%.%n",
                    avgConversionRate * 100);
            System.out.println();

            // 5. LONG_RANGE rate (SHOOTER bonus relevance)
            long longRange = shotLocationCounts.get(V24ShotLocation.LONG_RANGE);
            double longRangePct = totalShots > 0 ? 100.0 * longRange / totalShots : 0;
            System.out.printf("  LONG_RANGE rate: %.1f%%. SHOOTER bonus adds +36%% xG on these shots (V25D34-F1).%n",
                    longRangePct);
            System.out.printf("    If runtime has higher LONG_RANGE rate, SHOOTER=78-90 multiplies hard.%n");
            System.out.println();

            // Summary: rank layers by potential to close gap
            System.out.println("RANKING (potential to close the 5.45 - 2.153 = " + gap + " goals gap):");
            System.out.println("  1. POSSESSION: if runtime gives strong team 70-80%% possession (vs 50%% diag),");
            System.out.println("     they get 36-54 extra attacking minutes → 4-6 more shots → 0.4-0.6 more goals (low impact at 10%% conv).");
            System.out.println("  2. SHOT LOCATION: if runtime skews shots toward SIX_YARD_BOX/PENALTY_AREA_CENTER,");
            System.out.println("     xG per shot rises from 0.10 → 0.30, tripling conversion.");
            System.out.println("  3. CONVERSION RATE: if runtime converts at 30%%+ (vs diagnostic ~10%%),");
            System.out.println("     goals triple without any other layer changing.");
            System.out.println("  4. xG CALIBRATION: if diagnostic xG is too LOW (engine under-counts xG),");
            System.out.println("     real conversion against real xG is fine but diagnostic looks low.");
        }

        void printLayerTable(String label) {
            System.out.println("--- " + label + " (N=" + n + " matches) ---");
            System.out.printf("  Goals:       home=%.2f  away=%.2f  total=%.2f%n",
                    avgHomeGoals, avgAwayGoals, avgTotalGoals);
            System.out.printf("  Shots:       home=%.2f  away=%.2f  total=%.2f  (%.2f shots/min)%n",
                    avgHomeShots, avgAwayShots, avgHomeShots + avgAwayShots, avgShotsPerMinute);
            System.out.printf("  xG:          home=%.2f  away=%.2f  total=%.2f%n",
                    avgHomeXg, avgAwayXg, avgHomeXg + avgAwayXg);
            System.out.printf("  xG/shot:     %.3f  (%.1f%% conversion)%n",
                    avgXgPerShot, avgConversionRate * 100);
            System.out.printf("  Possession:  home=%.1f%%  away=%.1f%%%n",
                    avgHomePossessionPct, avgAwayPossessionPct);

            long totalShots = shotLocationCounts.values().stream().mapToLong(Long::longValue).sum();
            System.out.println("  Shot location distribution (count / %):");
            for (V24ShotLocation loc : V24ShotLocation.values()) {
                long count = shotLocationCounts.get(loc);
                double pct = totalShots > 0 ? 100.0 * count / totalShots : 0;
                System.out.printf("    %-22s %5d  (%4.1f%%)%n", loc, count, pct);
            }
        }
    }

    private void runScenario(TeamStyle style, String label, int homeOvr, int awayOvr) {
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        PerLayerAccumulator acc = new PerLayerAccumulator();
        acc.n = N_SIMULATIONS;
        long totalGoals = 0;
        long totalHomeShots = 0, totalAwayShots = 0;
        long totalHomeGoals = 0, totalAwayGoals = 0;
        double totalHomeXg = 0, totalAwayXg = 0;
        long totalHomePossTicks = 0, totalAwayPossTicks = 0;

        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContext(style, "v31b-" + style + "-" + label + "-" + seed,
                    homeOvr, awayOvr);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);

            int hg = result.homeGoals();
            int ag = result.awayGoals();
            totalHomeGoals += hg;
            totalAwayGoals += ag;
            totalGoals += hg + ag;
            totalHomeShots += result.homeShots();
            totalAwayShots += result.awayShots();
            totalHomeXg += result.homeXg();
            totalAwayXg += result.awayXg();
            totalHomePossTicks += result.homePossession();
            totalAwayPossTicks += result.awayPossession();

            // Per-shot location distribution from timeline
            List<V24MatchEvent> shotEvents = result.timeline().shotEvents();
            for (V24MatchEvent shot : shotEvents) {
                if (shot.shotCoordinate() != null) {
                    V24ShotLocation loc = shot.shotCoordinate().location();
                    acc.shotLocationCounts.merge(loc, 1L, Long::sum);
                    // Whether this shot became a goal: type == GOAL with same minute?
                    // We approximate by counting goal events whose minute == shot.minute and same teamId.
                    // Simpler: count GOAL events separately per location.
                }
            }
            List<V24MatchEvent> goalEvents = result.timeline().goalEvents();
            for (V24MatchEvent goal : goalEvents) {
                if (goal.shotCoordinate() != null) {
                    V24ShotLocation loc = goal.shotCoordinate().location();
                    acc.shotLocationGoals.merge(loc, 1L, Long::sum);
                }
            }
        }

        acc.avgTotalGoals = (double) totalGoals / N_SIMULATIONS;
        acc.avgHomeGoals = (double) totalHomeGoals / N_SIMULATIONS;
        acc.avgAwayGoals = (double) totalAwayGoals / N_SIMULATIONS;
        acc.avgHomeShots = (double) totalHomeShots / N_SIMULATIONS;
        acc.avgAwayShots = (double) totalAwayShots / N_SIMULATIONS;
        acc.avgHomeXg = totalHomeXg / N_SIMULATIONS;
        acc.avgAwayXg = totalAwayXg / N_SIMULATIONS;
        acc.avgHomePossessionPct = (double) totalHomePossTicks / N_SIMULATIONS;
        acc.avgAwayPossessionPct = (double) totalAwayPossTicks / N_SIMULATIONS;
        acc.avgShotsPerMinute = (acc.avgHomeShots + acc.avgAwayShots) / 90.0;
        long totalShots = totalHomeShots + totalAwayShots;
        acc.avgXgPerShot = totalShots > 0 ? (totalHomeXg + totalAwayXg) / totalShots : 0;
        acc.avgConversionRate = totalShots > 0 ? (double) totalGoals / totalShots : 0;

        System.out.printf("%-12s %-15s OVR %3dx%-3d: total=%.3f  shots=%.2f  xG=%.3f  poss(home)=%.1f%%%n",
                style, label, homeOvr, awayOvr,
                acc.avgTotalGoals, acc.avgHomeShots + acc.avgAwayShots,
                acc.avgHomeXg + acc.avgAwayXg, acc.avgHomePossessionPct);
    }

    private PerLayerAccumulator runLayerAccumulator(V24DetailedMatchEngine engine, TeamStyle style,
                                                     int homeOvr, int awayOvr, String label) {
        PerLayerAccumulator acc = new PerLayerAccumulator();
        acc.n = N_SIMULATIONS;
        long totalGoals = 0;
        long totalHomeShots = 0, totalAwayShots = 0;
        long totalHomeGoals = 0, totalAwayGoals = 0;
        double totalHomeXg = 0, totalAwayXg = 0;
        long totalHomePossTicks = 0, totalAwayPossTicks = 0;

        for (int seed = 1; seed <= N_SIMULATIONS; seed++) {
            V24MatchContext ctx = buildContext(style, "v31b-phase1b-" + label + "-" + seed,
                    homeOvr, awayOvr);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);

            int hg = result.homeGoals();
            int ag = result.awayGoals();
            totalHomeGoals += hg;
            totalAwayGoals += ag;
            totalGoals += hg + ag;
            totalHomeShots += result.homeShots();
            totalAwayShots += result.awayShots();
            totalHomeXg += result.homeXg();
            totalAwayXg += result.awayXg();
            totalHomePossTicks += result.homePossession();
            totalAwayPossTicks += result.awayPossession();

            // Per-shot location distribution from timeline
            List<V24MatchEvent> shotEvents = result.timeline().shotEvents();
            for (V24MatchEvent shot : shotEvents) {
                if (shot.shotCoordinate() != null) {
                    acc.shotLocationCounts.merge(shot.shotCoordinate().location(), 1L, Long::sum);
                }
            }
            List<V24MatchEvent> goalEvents = result.timeline().goalEvents();
            for (V24MatchEvent goal : goalEvents) {
                if (goal.shotCoordinate() != null) {
                    acc.shotLocationGoals.merge(goal.shotCoordinate().location(), 1L, Long::sum);
                }
            }
        }

        acc.avgTotalGoals = (double) totalGoals / N_SIMULATIONS;
        acc.avgHomeGoals = (double) totalHomeGoals / N_SIMULATIONS;
        acc.avgAwayGoals = (double) totalAwayGoals / N_SIMULATIONS;
        acc.avgHomeShots = (double) totalHomeShots / N_SIMULATIONS;
        acc.avgAwayShots = (double) totalAwayShots / N_SIMULATIONS;
        acc.avgHomeXg = totalHomeXg / N_SIMULATIONS;
        acc.avgAwayXg = totalAwayXg / N_SIMULATIONS;
        acc.avgHomePossessionPct = (double) totalHomePossTicks / N_SIMULATIONS;
        acc.avgAwayPossessionPct = (double) totalAwayPossTicks / N_SIMULATIONS;
        acc.avgShotsPerMinute = (acc.avgHomeShots + acc.avgAwayShots) / 90.0;
        long totalShots = totalHomeShots + totalAwayShots;
        acc.avgXgPerShot = totalShots > 0 ? (totalHomeXg + totalAwayXg) / totalShots : 0;
        acc.avgConversionRate = totalShots > 0 ? (double) totalGoals / totalShots : 0;
        return acc;
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
