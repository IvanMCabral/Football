package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V25D70-C31 Phase 1e — Form validation diagnostic.
 *
 * <p><b>Why this exists:</b> V31d showed synthetic attrs alone produce 3.9% conversion
 * (NOT runtime 9.7%). But V31d's discovery was the shooterQuality formula in
 * {@link com.footballmanager.application.service.simulation.v24.V24PlayerSelector}:
 * <pre>
 *   shooterQuality = (attackNorm * 0.6 + formNorm * 0.4)
 * </pre>
 * where {@code formNorm = form / 100}. With default form=50, formNorm=0.50. But
 * V24FormMutationApplier mutates form after every match (rating≥8.0 → +3, etc.),
 * so post-R1 players have form ~75-90 in runtime.
 *
 * <p>This test validates the form hypothesis by setting form=80 on all players
 * (simulating "post-R1 typical" runtime state) and checking if conversion rate
 * moves toward runtime's 9.7%.
 *
 * <p><b>Hypothesis confirmation table (parent's spec):</b>
 * <table>
 *   <tr><th>If V31e conversion rate is...</th><th>Root cause is...</th><th>Phase 2 fix</th></tr>
 *   <tr><td>~9.7% (matches runtime 5.45/56 shots)</td>
 *       <td>Form mutation IS the multiplier</td>
 *       <td>Adjust shooterQuality weighting from 60/40 to 80/20 or 90/10 (reduce form impact)</td></tr>
 *   <tr><td>~3.5% (matches V31d/V31b/V31c diagnostic)</td>
 *       <td>Form is NOT the multiplier — continue hunting</td>
 *       <td>Search V24PlayerMatchStatsModel, V24ContextFactory.build, V24LiveSession, LeagueSimulator</td></tr>
 * </table>
 *
 * <p><b>Test setup:</b> Same as V31d (12 synthetic teams, random 50-90 attrs, NO skills,
 * NO height) BUT form=80 on all players (vs V31d's default form=50).
 *
 * <p><b>Usage:</b>
 * <pre>
 *   mvn test -Dtest=V31eFormValidationDiagnosticTest -DfailIfNoTests=false
 * </pre>
 */
class V31eFormValidationDiagnosticTest {

    private static final int N_PER_PAIRING = 25;
    private static final long SEED = 20260628L;
    private static final int FORM_VALUE = 80;  // post-R1 typical runtime state

    private static final double RUNTIME_CONVERSION_RATE = 0.097;
    private static final double V31D_CONVERSION_RATE = 0.039;
    private static final double V31B_CONVERSION_RATE = 0.038;
    private static final double V31C_CONVERSION_RATE = 0.030;

    @Test
    @DisplayName("V31e: 12-team synthetic league, form=80 on all players — N=50 matches")
    void form80_synthetic_league() {
        Random random = new Random(SEED);

        List<SyntheticTeam> teams = generate12SyntheticTeams(random);
        SyntheticTeam realMadrid = teams.get(0);
        SyntheticTeam barcelona = teams.get(1);
        List<SyntheticTeam> opponents = teams.subList(2, 12);

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D70-C31 PHASE-1e FORM VALIDATION DIAGNOSTIC");
        System.out.println("Same as V31d BUT form=" + FORM_VALUE + " on all players");
        System.out.println("================================================================");
        System.out.println();
        System.out.println("Real Madrid (500M) starting 11 — form=" + FORM_VALUE + " on all:");
        printRoster(realMadrid);
        System.out.println();
        System.out.println("Barcelona (400M) starting 11 — form=" + FORM_VALUE + " on all:");
        printRoster(barcelona);
        System.out.println();

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        PerMatchMetrics rmMetrics = runMatches(engine, realMadrid, opponents, N_PER_PAIRING,
                "Real Madrid");
        PerMatchMetrics bcMetrics = runMatches(engine, barcelona, opponents, N_PER_PAIRING,
                "Barcelona");

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V31e RESULTS");
        System.out.println("================================================================");
        rmMetrics.printSummary("REAL MADRID (500M, form=" + FORM_VALUE + ")");
        bcMetrics.printSummary("BARCELONA (400M, form=" + FORM_VALUE + ")");

        PerMatchMetrics overall = PerMatchMetrics.aggregate(rmMetrics, bcMetrics);
        System.out.println();
        overall.printSummary("OVERALL (RM + Barca vs mid-tier, form=" + FORM_VALUE + ", N=" + overall.matchCount + ")");

        double conversionRate = overall.totalGoals > 0
                ? (double) overall.totalGoals / overall.totalShots : 0;

        System.out.println();
        System.out.println("HYPOTHESIS CONFIRMATION (form hypothesis):");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("  V31e (form=%d) intermedios avg goals     = %.2f%n",
                FORM_VALUE, (double) overall.totalGoals / overall.matchCount);
        System.out.printf("  V31e conversion rate (goals/shots)        = %.3f (%4.1f%%)%n",
                conversionRate, conversionRate * 100);
        System.out.printf("  V31d (form=50) conversion rate             = %.3f (%4.1f%%)%n",
                V31D_CONVERSION_RATE, V31D_CONVERSION_RATE * 100);
        System.out.printf("  V31b diagnostic (synthetic per-pos attrs) = %.3f (%4.1f%%)%n",
                V31B_CONVERSION_RATE, V31B_CONVERSION_RATE * 100);
        System.out.printf("  V31c (real LaLiga roster)                 = %.3f (%4.1f%%)%n",
                V31C_CONVERSION_RATE, V31C_CONVERSION_RATE * 100);
        System.out.printf("  Runtime implied (5.45 / 56 shots)         = %.3f (%4.1f%%)%n",
                RUNTIME_CONVERSION_RATE, RUNTIME_CONVERSION_RATE * 100);
        System.out.println();
        if (Math.abs(conversionRate - RUNTIME_CONVERSION_RATE) < 0.02) {
            System.out.println("  >>> H_form CONFIRMED: V31e (form=" + FORM_VALUE + ") conversion ≈ runtime (within 2pp).");
            System.out.println("      Form mutation across career rounds IS the runtime multiplier.");
            System.out.println("      Phase 2 fix: adjust shooterQuality weighting from 60/40 to 80/20 or 90/10.");
            System.out.println("      (Reduces form impact from 40% to 10-20%, matching realistic football variance.)");
        } else if (Math.abs(conversionRate - V31D_CONVERSION_RATE) < 0.02) {
            System.out.println("  >>> H_form REJECTED: V31e ≈ V31d (form=50) within 2pp.");
            System.out.println("      Form is NOT the multiplier. Continue hunting in:");
            System.out.println("      - V24PlayerMatchStatsModel (per-match player ratings)");
            System.out.println("      - V24ContextFactory.build (per-match context setup)");
            System.out.println("      - V24LiveSession (live session wrapping)");
            System.out.println("      - LeagueSimulator (round-level simulation)");
        } else {
            System.out.printf("  >>> INCONCLUSIVE: V31e conversion=%.3f between V31d (%.3f) and runtime (%.3f).%n",
                    conversionRate, V31D_CONVERSION_RATE, RUNTIME_CONVERSION_RATE);
            System.out.println("      Form has SOME impact (Δ conversion = " +
                    String.format("%.1f", (conversionRate - V31D_CONVERSION_RATE) * 100) +
                    "pp) but does not fully explain the gap.");
            System.out.println("      Phase 2: combine form-fix with another layer reduction.");
        }
        System.out.println("================================================================");

        assertTrue(overall.matchCount == 2 * N_PER_PAIRING,
                "V31e should run " + (2 * N_PER_PAIRING) + " matches; got " + overall.matchCount);
    }

    // ========== Synthetic team generation (same as V31d) ==========

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
        teams.add(buildSyntheticTeam(random, "Real Madrid", 500));
        teams.add(buildSyntheticTeam(random, "Barcelona", 400));
        for (int i = 0; i < 10; i++) {
            String name = midTierPrefixes[i] + " " + midTierSuffixes[i];
            long budget = 9 + random.nextInt(12);
            teams.add(buildSyntheticTeam(random, name, budget));
        }
        return teams;
    }

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

    private SessionPlayer buildSyntheticPlayer(Random random, String teamName, int index, String position) {
        int age = 18 + random.nextInt(17);
        int attack = 50 + random.nextInt(41);
        int defense = 50 + random.nextInt(41);
        int technique = 50 + random.nextInt(41);
        int speed = 50 + random.nextInt(41);
        int stamina = 50 + random.nextInt(41);
        int mentality = 50 + random.nextInt(41);
        String id = "v31e-" + teamName.replaceAll("\\s+", "") + "-p" + index;
        SessionPlayer p = SessionPlayer.custom(
                id, age, position,
                attack, defense, technique, speed, stamina, mentality,
                BigDecimal.valueOf(50_000));
        // KEY DIFFERENCE vs V31d: set form=FORM_VALUE on all players
        // to simulate "post-R1 typical" runtime state
        p.setForm(FORM_VALUE);
        return p;
    }

    private void printRoster(SyntheticTeam team) {
        System.out.printf("  Budget: %dM, avgOverall: %.1f, form=%d on all players%n",
                team.budgetMillions, team.avgOverall, FORM_VALUE);
        for (SessionPlayer p : team.players) {
            Integer ovr = p.calculateOverall();
            Integer form = p.getForm();
            System.out.printf("    %-22s %-6s age=%d OVR=%-3s  attack=%d defense=%d form=%s%n",
                    p.getName(), p.getPosition(), p.getAge(), ovr == null ? "?" : ovr.toString(),
                    p.getAttack(), p.getDefense(),
                    form == null ? "null" : form.toString());
        }
    }

    private static final class PerMatchMetrics {
        int matchCount;
        long totalGoals;
        long totalShots;
        double totalXg;

        static PerMatchMetrics aggregate(PerMatchMetrics... accs) {
            PerMatchMetrics agg = new PerMatchMetrics();
            for (PerMatchMetrics a : accs) {
                agg.matchCount += a.matchCount;
                agg.totalGoals += a.totalGoals;
                agg.totalShots += a.totalShots;
                agg.totalXg += a.totalXg;
            }
            return agg;
        }

        void add(V24DetailedMatchResult r) {
            matchCount++;
            totalGoals += r.homeGoals() + r.awayGoals();
            totalShots += r.homeShots() + r.awayShots();
            totalXg += r.homeXg() + r.awayXg();
        }

        void printSummary(String label) {
            double avgGoals = (double) totalGoals / matchCount;
            double avgShots = (double) totalShots / matchCount;
            double avgXg = totalXg / matchCount;
            double xgPerShot = totalShots > 0 ? totalXg / totalShots : 0;
            double convRate = totalShots > 0 ? (double) totalGoals / totalShots : 0;

            System.out.printf("--- %s (N=%d) ---%n", label, matchCount);
            System.out.printf("  Goals:      %.2f avg%n", avgGoals);
            System.out.printf("  Shots:      %.2f avg (xG/shot=%.3f)%n", avgShots, xgPerShot);
            System.out.printf("  xG:         %.2f%n", avgXg);
            System.out.printf("  Conversion: %.1f%% (goals/shots)%n", convRate * 100);
        }
    }

    private PerMatchMetrics runMatches(V24DetailedMatchEngine engine, SyntheticTeam homeTeam,
                                       List<SyntheticTeam> opponents, int nPerPairing, String homeLabel) {
        PerMatchMetrics metrics = new PerMatchMetrics();
        for (int i = 0; i < nPerPairing; i++) {
            SyntheticTeam opp = opponents.get(i % opponents.size());
            V24MatchContext ctx = buildContext(homeTeam, opp, i + 1);
            V24DetailedMatchResult result = engine.simulate(ctx, i + 1);
            metrics.add(result);
        }
        return metrics;
    }

    private V24MatchContext buildContext(SyntheticTeam home, SyntheticTeam away, int seed) {
        SessionTeam homeSessionTeam = makeTeam("home-v31e-" + seed, home.name);
        SessionTeam awaySessionTeam = makeTeam("away-v31e-" + seed, away.name);
        return new V24MatchContext(
                "v31e-" + home.name + "-vs-" + away.name + "-" + seed,
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
                BigDecimal.valueOf(500_000_000L),
                "4-3-3", null);
    }
}
