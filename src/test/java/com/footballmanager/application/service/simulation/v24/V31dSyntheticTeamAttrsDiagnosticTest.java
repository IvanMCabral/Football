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
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V25D70-C31 Phase 1d — Synthetic team attrs diagnostic (replicating C30 smoke setup).
 *
 * <p><b>Why this exists:</b> V31a/V31b/V31c all used either synthetic attrs (V31a/b)
 * or real LaLiga seed attrs (V31c). None reproduced runtime C30 smoke's 9.7%
 * conversion rate (intermedios avg 5.45 goals).
 *
 * <p>The C30 smoke user (`smoke-c28-20260628-1714@test.com`) used a 12-team
 * synthetic league with budgets 500M/400M/9-20M. The setup likely used
 * {@link com.footballmanager.application.service.world.WorldPlayerCommandService#createRandomPlayer}
 * which generates players with attrs uniform-random in [50, 90] — independent of
 * team budget. This means all 12 teams have similar OVR distribution (~70 avg).
 *
 * <p><b>Hypothesis:</b> Synthetic random players (50-90 attrs) reproduce runtime 9.7% conversion
 * because the engine treats "random high" players (e.g. ATT with attack=89) the same as
 * "Real Madrid star" players, but with budget = "Random FC 9M" — the OVR is similar
 * but the team composition in runtime may have NON-uniform per-position variance that
 * the engine amplifies differently than my V31c (top-rated per position).
 *
 * <p><b>Test setup:</b>
 * <ol>
 *   <li>Generate 12 synthetic teams matching C30 smoke:
 *       <ul>
 *         <li>2 "top" teams (Real Madrid 500M, Barcelona 400M) — players with attrs uniform [50, 90]</li>
 *         <li>10 "mid-tier" teams (others 9-20M) — players with attrs uniform [50, 90]</li>
 *       </ul>
 *   </li>
 *   <li>Each team has 11 players: 1 GK + 4 DEF + 3 MID + 2 WINGER + 1 ATT</li>
 *   <li>NO skills (createRandomPlayer doesn't generate skills → engine uses defaults)</li>
 *   <li>NO heights (createRandomPlayer doesn't set heights → no AERIAL/HEADER compounding)</li>
 *   <li>N=50 matches: 25 Real Madrid vs mid-tier + 25 Barcelona vs mid-tier (alternating)</li>
 *   <li>Style: BALANCED</li>
 * </ol>
 *
 * <p><b>Hypothesis confirmation table (parent's spec):</b>
 * <table>
 *   <tr><th>If V31d conversion rate is...</th><th>Root cause is...</th><th>Phase 2 fix</th></tr>
 *   <tr><td>~9.7% (matches runtime 5.45/56 shots)</td>
 *       <td>Synthetic team attrs are the root cause (budget-based inflation)</td>
 *       <td>Adjust CareerSetupService / career setup so synthetic teams have realistic attrs</td></tr>
 *   <tr><td>~3.0% (matches V31b/V31c diagnostic)</td>
 *       <td>There's another runtime-only multiplier (chemistry, fatigue, multi-match, etc.)</td>
 *       <td>Hunt the multiplier in V23 MatchEngineImpl, V24LiveSession, LeagueSimulator, CareerSimulator</td></tr>
 * </table>
 *
 * <p><b>Usage:</b>
 * <pre>
 *   mvn test -Dtest=V31dSyntheticTeamAttrsDiagnosticTest -DfailIfNoTests=false
 * </pre>
 */
class V31dSyntheticTeamAttrsDiagnosticTest {

    private static final int N_PER_PAIRING = 25;
    private static final long SEED = 20260628L;  // C30 smoke date

    // Runtime observed (post-C29, REVISOR C30 smoke report)
    private static final double RUNTIME_INTERMEDIOS_AVG = 5.45;
    private static final double RUNTIME_CONVERSION_RATE = 0.097;
    // V31c diagnostic baseline (real LaLiga roster)
    private static final double V31C_CONVERSION_RATE = 0.030;
    // V31b synthetic diagnostic (per-position attrs + LaLiga profile)
    private static final double V31B_CONVERSION_RATE = 0.038;

    @Test
    @DisplayName("V31d: 12-team synthetic league (500M/400M/9-20M, random 50-90 attrs) — N=50 matches")
    void synthetic_12_team_league_runtime_replica() {
        Random random = new Random(SEED);

        // Generate 12 synthetic teams
        List<SyntheticTeam> teams = generate12SyntheticTeams(random);
        SyntheticTeam realMadrid = teams.get(0);
        SyntheticTeam barcelona = teams.get(1);

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D70-C31 PHASE-1d SYNTHETIC TEAM ATTRS DIAGNOSTIC");
        System.out.println("Replicating C30 smoke: 12 teams, budgets 500M/400M/9-20M");
        System.out.println("Players: random attrs uniform [50, 90], NO skills, NO height");
        System.out.println("================================================================");
        System.out.println();
        System.out.println("Real Madrid (500M) starting 11:");
        printRoster(realMadrid);
        System.out.println();
        System.out.println("Barcelona (400M) starting 11:");
        printRoster(barcelona);
        System.out.println();

        // Build opponents (the 10 mid-tier teams)
        List<SyntheticTeam> opponents = teams.subList(2, 12);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        PerMatchMetrics rmMetrics = runMatches(engine, realMadrid, opponents, N_PER_PAIRING,
                "Real Madrid");
        PerMatchMetrics bcMetrics = runMatches(engine, barcelona, opponents, N_PER_PAIRING,
                "Barcelona");

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V31d RESULTS");
        System.out.println("================================================================");
        rmMetrics.printSummary("REAL MADRID (500M, random attrs)");
        bcMetrics.printSummary("BARCELONA (400M, random attrs)");

        // Aggregate overall (N=50 matches)
        PerMatchMetrics overall = PerMatchMetrics.aggregate(rmMetrics, bcMetrics);
        System.out.println();
        overall.printSummary("OVERALL (RM + Barca vs mid-tier, N=" + overall.matchCount + ")");

        double conversionRate = overall.totalGoals > 0
                ? (double) overall.totalGoals / overall.totalShots : 0;

        System.out.println();
        System.out.println("HYPOTHESIS CONFIRMATION (using overall aggregate):");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("  V31d intermedios avg goals     = %.2f%n", (double) overall.totalGoals / overall.matchCount);
        System.out.printf("  V31d conversion rate (goals/shots)        = %.3f (%4.1f%%)%n",
                conversionRate, conversionRate * 100);
        System.out.printf("  V31c diagnostic (real LaLiga roster)     = %.3f (%4.1f%%)%n",
                V31C_CONVERSION_RATE, V31C_CONVERSION_RATE * 100);
        System.out.printf("  V31b diagnostic (synthetic per-pos attrs) = %.3f (%4.1f%%)%n",
                V31B_CONVERSION_RATE, V31B_CONVERSION_RATE * 100);
        System.out.printf("  Theoretical (line 700, intensity=0.82)   = %.3f (%4.1f%%)%n",
                0.067, 67.0);
        System.out.printf("  Runtime implied (5.45 / 56 shots)         = %.3f (%4.1f%%)%n",
                RUNTIME_CONVERSION_RATE, RUNTIME_CONVERSION_RATE * 100);
        System.out.println();
        if (Math.abs(conversionRate - RUNTIME_CONVERSION_RATE) < 0.02) {
            System.out.println("  >>> SYNTHETIC ATTRS CONFIRMED: V31d conversion rate ≈ runtime (within 2pp).");
            System.out.println("      Root cause: synthetic random players (50-90 attrs) inflate goal conversion.");
            System.out.println("      Phase 2: fix CareerSetupService / createRandomPlayer to produce realistic attrs.");
            System.out.println("      (e.g., scale player attrs by team budget, or use LaLiga-style hardcoded top teams).");
        } else if (Math.abs(conversionRate - V31C_CONVERSION_RATE) < 0.02
                || Math.abs(conversionRate - V31B_CONVERSION_RATE) < 0.02) {
            System.out.println("  >>> ANOTHER RUNTIME MULTIPLIER: V31d ≈ V31c/V31b diagnostic.");
            System.out.println("      Synthetic attrs are NOT the cause. Some other runtime-only effect inflates goals.");
            System.out.println("      Phase 2: hunt multiplier in V23 MatchEngineImpl, V24LiveSession, LeagueSimulator, CareerSimulator.");
        } else {
            System.out.printf("  >>> INCONCLUSIVE: V31d conversion=%.3f does not match any reference (within 2pp).%n",
                    conversionRate);
            System.out.println("      Need deeper analysis — likely multi-layer interaction.");
        }
        System.out.println("================================================================");

        assertTrue(overall.matchCount == 2 * N_PER_PAIRING,
                "V31d should run " + (2 * N_PER_PAIRING) + " matches; got " + overall.matchCount);
    }

    // ========== Synthetic team generation (mirrors createRandomPlayer) ==========

    /**
     * Synthetic team record: name + budget + SessionPlayer roster.
     */
    private static final class SyntheticTeam {
        String name;
        long budgetMillions;
        List<SessionPlayer> players;
        double avgOverall;

        SyntheticTeam(String name, long budgetMillions, List<SessionPlayer> players) {
            this.name = name;
            this.budgetMillions = budgetMillions;
            this.players = players;
            this.avgOverall = players.stream()
                    .mapToInt(p -> p.calculateOverall() == null ? 50 : p.calculateOverall())
                    .average()
                    .orElse(70);
        }
    }

    /**
     * Generate 12 synthetic teams matching C30 smoke setup:
     * - Real Madrid 500M, Barcelona 400M (the 2 top teams)
     * - 10 mid-tier teams with budgets 9-20M (random names)
     *
     * Players generated with attrs uniform-random [50, 90] per
     * WorldPlayerCommandService.createRandomPlayer logic (no skill, no height).
     */
    private List<SyntheticTeam> generate12SyntheticTeams(Random random) {
        String[] midTierPrefixes = {
                "Sporting", "Deportivo", "Club Atlético", "Nacional", "River",
                "Atlético", "Olimpia", "Real", "FC", "Unión"
        };
        String[] midTierSuffixes = {
                "Amarillo", "Verde", "Azul", "Negro", "Plate",
                "Naranja", "Celeste", "Rojo", "Blanco", "Roja"
        };

        List<SyntheticTeam> teams = new ArrayList<>();
        // Top 2 teams
        teams.add(buildSyntheticTeam(random, "Real Madrid", 500));
        teams.add(buildSyntheticTeam(random, "Barcelona", 400));
        // 10 mid-tier teams
        for (int i = 0; i < 10; i++) {
            String name = midTierPrefixes[i] + " " + midTierSuffixes[i];
            long budget = 9 + random.nextInt(12);  // 9-20M
            teams.add(buildSyntheticTeam(random, name, budget));
        }
        return teams;
    }

    /**
     * Build a 11-player synthetic team: 1 GK + 4 DEF + 3 MID + 2 WINGER + 1 ATT.
     * Each player has attrs uniform-random in [50, 90], NO skills, NO height.
     */
    private SyntheticTeam buildSyntheticTeam(Random random, String name, long budgetMillions) {
        String[] positions = {"GK", "DEF", "DEF", "DEF", "DEF",
                "MID", "MID", "MID",
                "WINGER", "WINGER",
                "ATT"};
        List<SessionPlayer> players = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            players.add(buildSyntheticPlayer(random, name, i, positions[i]));
        }
        return new SyntheticTeam(name, budgetMillions, players);
    }

    /**
     * Build a synthetic player mirroring WorldPlayerCommandService.createRandomPlayer.
     * attrs uniform [50, 90], NO skill levels, NO height.
     */
    private SessionPlayer buildSyntheticPlayer(Random random, String teamName, int index, String position) {
        int age = 18 + random.nextInt(17);  // 18-34
        int attack = 50 + random.nextInt(41);     // [50, 90]
        int defense = 50 + random.nextInt(41);
        int technique = 50 + random.nextInt(41);
        int speed = 50 + random.nextInt(41);
        int stamina = 50 + random.nextInt(41);
        int mentality = 50 + random.nextInt(41);
        String id = "v31d-" + teamName.replaceAll("\\s+", "") + "-p" + index;
        return SessionPlayer.custom(
                id, age, position,
                attack, defense, technique, speed, stamina, mentality,
                BigDecimal.valueOf(50_000));
        // No skills set, no height set — mirrors createRandomPlayer default behavior
    }

    private void printRoster(SyntheticTeam team) {
        System.out.printf("  Budget: %dM, avgOverall: %.1f%n", team.budgetMillions, team.avgOverall);
        for (SessionPlayer p : team.players) {
            Integer ovr = p.calculateOverall();
            Map<PlayerSkill, Integer> skills = p.getSkillLevels();
            System.out.printf("    %-22s %-6s age=%d OVR=%-3s  attack=%d defense=%d tech=%d speed=%d stamina=%d mentality=%d (skills=%d)%n",
                    p.getName(), p.getPosition(), p.getAge(), ovr == null ? "?" : ovr.toString(),
                    p.getAttack(), p.getDefense(), p.getTechnique(), p.getSpeed(),
                    p.getStamina(), p.getMentality(),
                    skills.size());
        }
    }

    // ========== Match running + metrics ==========

    private static final class PerMatchMetrics {
        int matchCount;
        long totalGoals;
        long totalShots;
        double totalXg;
        long totalHomePossTicks;
        long totalAwayPossTicks;
        EnumMap<V24ShotLocation, Long> shotLocationCounts = new EnumMap<>(V24ShotLocation.class);

        PerMatchMetrics() {
            for (V24ShotLocation loc : V24ShotLocation.values()) {
                shotLocationCounts.put(loc, 0L);
            }
        }

        static PerMatchMetrics aggregate(PerMatchMetrics... accs) {
            PerMatchMetrics agg = new PerMatchMetrics();
            for (PerMatchMetrics a : accs) {
                agg.matchCount += a.matchCount;
                agg.totalGoals += a.totalGoals;
                agg.totalShots += a.totalShots;
                agg.totalXg += a.totalXg;
                agg.totalHomePossTicks += a.totalHomePossTicks;
                agg.totalAwayPossTicks += a.totalAwayPossTicks;
                for (V24ShotLocation loc : V24ShotLocation.values()) {
                    agg.shotLocationCounts.merge(loc, a.shotLocationCounts.get(loc), Long::sum);
                }
            }
            return agg;
        }

        void add(V24DetailedMatchResult r) {
            matchCount++;
            totalGoals += r.homeGoals() + r.awayGoals();
            totalShots += r.homeShots() + r.awayShots();
            totalXg += r.homeXg() + r.awayXg();
            totalHomePossTicks += r.homePossession();
            totalAwayPossTicks += r.awayPossession();
            for (V24MatchEvent shot : r.timeline().shotEvents()) {
                if (shot.shotCoordinate() != null) {
                    shotLocationCounts.merge(shot.shotCoordinate().location(), 1L, Long::sum);
                }
            }
        }

        void printSummary(String label) {
            double avgGoals = (double) totalGoals / matchCount;
            double avgShots = (double) totalShots / matchCount;
            double avgXg = totalXg / matchCount;
            double xgPerShot = totalShots > 0 ? totalXg / totalShots : 0;
            double convRate = totalShots > 0 ? (double) totalGoals / totalShots : 0;
            double homePoss = (double) totalHomePossTicks / matchCount;
            double awayPoss = (double) totalAwayPossTicks / matchCount;

            System.out.printf("--- %s (N=%d) ---%n", label, matchCount);
            System.out.printf("  Goals:      %.2f avg%n", avgGoals);
            System.out.printf("  Shots:      %.2f avg (%.2f shots/min)%n",
                    avgShots, avgShots / 90.0);
            System.out.printf("  xG:         %.2f (xG/shot=%.3f)%n", avgXg, xgPerShot);
            System.out.printf("  Conversion: %.1f%% (goals/shots)%n", convRate * 100);
            System.out.printf("  Possession: home=%.1f%% away=%.1f%%%n", homePoss, awayPoss);
            System.out.println("  Shot location distribution:");
            for (V24ShotLocation loc : V24ShotLocation.values()) {
                long count = shotLocationCounts.get(loc);
                double pct = totalShots > 0 ? 100.0 * count / totalShots : 0;
                System.out.printf("    %-22s %5d (%4.1f%%)%n", loc, count, pct);
            }
        }
    }

    private PerMatchMetrics runMatches(V24DetailedMatchEngine engine, SyntheticTeam homeTeam,
                                       List<SyntheticTeam> opponents, int nPerPairing, String homeLabel) {
        PerMatchMetrics metrics = new PerMatchMetrics();
        for (int i = 0; i < nPerPairing; i++) {
            // Cycle through opponents
            SyntheticTeam opp = opponents.get(i % opponents.size());
            V24MatchContext ctx = buildContext(homeTeam, opp, i + 1);
            V24DetailedMatchResult result = engine.simulate(ctx, i + 1);
            metrics.add(result);
        }
        return metrics;
    }

    private V24MatchContext buildContext(SyntheticTeam home, SyntheticTeam away, int seed) {
        SessionTeam homeSessionTeam = makeTeam("home-v31d-" + seed, home.name);
        SessionTeam awaySessionTeam = makeTeam("away-v31d-" + seed, away.name);
        return new V24MatchContext(
                "v31d-" + home.name + "-vs-" + away.name + "-" + seed,
                homeSessionTeam.getSessionTeamId(), awaySessionTeam.getSessionTeamId(),
                homeSessionTeam, awaySessionTeam,
                home.players, away.players,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED);
    }

    private SessionTeam makeTeam(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.valueOf(500_000_000L),  // 500M default budget
                "4-3-3", null);
    }
}
