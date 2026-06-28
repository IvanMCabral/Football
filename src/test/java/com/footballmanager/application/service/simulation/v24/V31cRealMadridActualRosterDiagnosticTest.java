package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.world.LaLigaSeedData;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V25D70-C31 Phase 1c — Real Madrid ACTUAL roster diagnostic (5 escenarios OVR-controlled).
 *
 * <p><b>Why this exists:</b> V31a/V31b used synthetic per-position attrs scaled by
 * team_ovr/85. V31c (v1) used Real Madrid actual roster but opponents were too close
 * (Real Sociedad avgOverall ~80, Atletico Madrid ~81, Mallorca ~76) — all in
 * PAREJOS intensity range (0.40-0.54). V31c v1 produced 1.33-1.60 goals avg vs
 * runtime intermedios 5.45 avg. But the comparison was unfair: runtime intermedios
 * involves intensity ~0.74 (opponents avgOverall ~70-75), not 0.40.
 *
 * <p><b>This v2:</b> uses Real Madrid actual roster against 5 OVR-controlled opponents
 * matching V31b's scenario structure (PAREJOS, INTERMEDIO-A/B/C, DESIGUALES), so we
 * can compare V31c diagnostic vs V31b diagnostic at the SAME intensity range.
 *
 * <p><b>Opponent selection (mid-tier LaLiga teams, by avgOverall):</b>
 * <ul>
 *   <li>PAREJOS (intensity 0.40, diffRatio &lt;5%): Real Sociedad (~80 OVR — closest to RM ~84)</li>
 *   <li>INTERMEDIO-A (intensity ~0.55, diffRatio ~10%): Mallorca (~76 OVR)</li>
 *   <li>INTERMEDIO-B (intensity ~0.65, diffRatio ~14%): Athletic Club (~73 OVR)</li>
 *   <li>INTERMEDIO-C (intensity ~0.74, diffRatio ~17%): Villarreal (~70 OVR)</li>
 *   <li>DESIGUALES (intensity 1.0, diffRatio &gt;30%): Real Valladolid (~60 OVR)</li>
 * </ul>
 *
 * <p><b>Hypothesis confirmation table (parent's spec):</b>
 * <table>
 *   <tr><th>If V31c conversion rate is...</th><th>Root cause is...</th><th>Phase 2 fix</th></tr>
 *   <tr><td>~9.7% (matches runtime 5.45/56 shots)</td>
 *       <td>H1 confirmed: xG per shot is HIGHER in runtime (shot quality layer)</td>
 *       <td>Investigate SHOOTER/HEADER multipliers or shot location bias</td></tr>
 *   <tr><td>~3.8% (matches V31b diagnostic)</td>
 *       <td>Runtime-only bug (engine + roster is OK, runtime engine path differs)</td>
 *       <td>Find the bug (stamina accumulation? substitution effect? live vs replay?)</td></tr>
 *   <tr><td>~6.7% (matches theoretical xG*0.82/0.60)</td>
 *       <td>Goal conversion formula in line 700 mis-applied</td>
 *       <td>Fix line 700 formula</td></tr>
 * </table>
 *
 * <p><b>Usage:</b>
 * <pre>
 *   mvn test -Dtest=V31cRealMadridActualRosterDiagnosticTest -DfailIfNoTests=false
 * </pre>
 */
class V31cRealMadridActualRosterDiagnosticTest {

    private static final int N_PER_SCENARIO = 30;

    // Runtime observed (post-C29, REVISOR C30 smoke report)
    private static final double RUNTIME_INTERMEDIOS_AVG = 5.45;
    private static final double RUNTIME_CONVERSION_RATE = 0.097;
    // V31b diagnostic baseline (synthetic attrs + skills ON)
    private static final double V31B_CONVERSION_RATE = 0.038;
    // Theoretical (line 700: xg * intensity / 0.60 for intermedios intensity=0.82)
    private static final double THEORETICAL_CONVERSION_RATE = 0.067;

    @Test
    @DisplayName("V31c: Real Madrid actual roster vs 5 OVR-controlled LaLiga opponents (N=150)")
    void realMadrid_vs_5_laLiga_opponents() {
        LaLigaSeedData seed = loadSeed();

        List<SessionPlayer> realMadridStart = buildStarting11(seed, "Real Madrid");

        // 5 opponents spanning the intensity range
        String[] opponentTeams = {"Real Sociedad", "Mallorca", "Athletic Club", "Villarreal", "Real Valladolid"};
        double[] expectedIntensities = {0.40, 0.55, 0.65, 0.74, 1.0};  // approx
        String[] scenarioLabels = {"PAREJOS (85x80)", "INT-A (85x76)", "INT-B (85x73)",
                "INT-C (85x70)", "DESIGUALES (85x60)"};

        System.out.println();
        System.out.println("================================================================");
        System.out.println("V25D70-C31 PHASE-1c REAL MADRID ACTUAL ROSTER DIAGNOSTIC (v2)");
        System.out.println("================================================================");
        System.out.println("Real Madrid starting 11 (top-rated per position):");
        printRoster(realMadridStart);
        System.out.println();

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        PerScenarioMetrics[] scenarios = new PerScenarioMetrics[5];
        for (int i = 0; i < 5; i++) {
            List<SessionPlayer> oppStart = buildStarting11(seed, opponentTeams[i]);
            System.out.println("--- " + opponentTeams[i] + " starting 11 (" + scenarioLabels[i] + ") ---");
            printRoster(oppStart);
            System.out.println();
            scenarios[i] = runScenario(engine, realMadridStart, "Real Madrid",
                    oppStart, opponentTeams[i], scenarioLabels[i], N_PER_SCENARIO);
        }

        System.out.println("================================================================");
        System.out.println("V31c RESULTS (Real Madrid actual roster, N=150 across 5 scenarios)");
        System.out.println("================================================================");
        for (int i = 0; i < 5; i++) {
            scenarios[i].printSummary(scenarioLabels[i], expectedIntensities[i]);
        }

        // Compute intermedios aggregate (INT-A + INT-B + INT-C)
        PerScenarioMetrics intermediosAgg = PerScenarioMetrics.aggregate(
                scenarios[1], scenarios[2], scenarios[3]);

        System.out.println();
        System.out.println("INTERMEDIOS AGGREGATE (A+B+C, N=" + (3 * N_PER_SCENARIO) + "):");
        intermediosAgg.printSummary("INTERMEDIOS", -1);

        double conversionRate = intermediosAgg.totalGoals > 0
                ? (double) intermediosAgg.totalGoals / intermediosAgg.totalShots : 0;
        double intermediosAvgGoals = (double) intermediosAgg.totalGoals / intermediosAgg.matchCount;

        System.out.println();
        System.out.println("HYPOTHESIS CONFIRMATION (using intermedios aggregate):");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("  V31c intermedios avg goals     = %.2f%n", intermediosAvgGoals);
        System.out.printf("  V31c conversion rate (goals/shots)        = %.3f (%4.1f%%)%n",
                conversionRate, conversionRate * 100);
        System.out.printf("  V31b diagnostic baseline conversion rate = %.3f (%4.1f%%)%n",
                V31B_CONVERSION_RATE, V31B_CONVERSION_RATE * 100);
        System.out.printf("  Theoretical (line 700, intensity=0.82)   = %.3f (%4.1f%%)%n",
                THEORETICAL_CONVERSION_RATE, THEORETICAL_CONVERSION_RATE * 100);
        System.out.printf("  Runtime implied (5.45 / 56 shots)         = %.3f (%4.1f%%)%n",
                RUNTIME_CONVERSION_RATE, RUNTIME_CONVERSION_RATE * 100);
        System.out.println();
        if (Math.abs(conversionRate - RUNTIME_CONVERSION_RATE) < 0.02) {
            System.out.println("  >>> H1 CONFIRMED: V31c conversion rate ≈ runtime (within 2pp).");
            System.out.println("      Root cause: runtime shots have HIGHER xG (shot quality layer).");
            System.out.println("      Phase 2: investigate shot quality bias (location, SHOOTER, HEADER).");
        } else if (Math.abs(conversionRate - V31B_CONVERSION_RATE) < 0.02) {
            System.out.println("  >>> RUNTIME-ONLY BUG: V31c conversion rate ≈ V31b diagnostic.");
            System.out.println("      Root cause: something in runtime engine adds goals.");
            System.out.println("      Phase 2: investigate runtime-only effects (stamina, chemistry, multi-match, live vs replay).");
        } else if (Math.abs(conversionRate - THEORETICAL_CONVERSION_RATE) < 0.02) {
            System.out.println("  >>> LINE 700 BUG: V31c conversion rate ≈ theoretical.");
            System.out.println("      Root cause: goal conversion formula is mis-applied.");
            System.out.println("      Phase 2: fix line 700 formula.");
        } else {
            System.out.println("  >>> INCONCLUSIVE: V31c conversion rate does NOT match any hypothesis.");
            System.out.printf("      V31c=%.3f (intermedios avg goals=%.2f, total=%d, shots=%d, xG=%.2f)%n",
                    conversionRate, intermediosAvgGoals, intermediosAgg.totalGoals,
                    intermediosAgg.totalShots, intermediosAgg.totalXg);
            System.out.println("      Need deeper analysis — likely multi-layer interaction.");
        }
        System.out.println("================================================================");

        // Sanity: V31c should produce N_PER_SCENARIO * 5 matches
        int totalMatches = N_PER_SCENARIO * 5;
        assertTrue(intermediosAgg.matchCount == 3 * N_PER_SCENARIO,
                "V31c intermedios aggregate should have " + (3 * N_PER_SCENARIO)
                + " matches; got " + intermediosAgg.matchCount);
    }

    // ========== Helpers ==========

    /**
     * Aggregates per-match metrics for one scenario.
     */
    private static final class PerScenarioMetrics {
        int matchCount;
        long totalGoals;
        long totalShots;
        double totalXg;
        EnumMap<V24ShotLocation, Long> shotLocationCounts = new EnumMap<>(V24ShotLocation.class);

        PerScenarioMetrics() {
            for (V24ShotLocation loc : V24ShotLocation.values()) {
                shotLocationCounts.put(loc, 0L);
            }
        }

        static PerScenarioMetrics aggregate(PerScenarioMetrics... accs) {
            PerScenarioMetrics agg = new PerScenarioMetrics();
            for (PerScenarioMetrics a : accs) {
                agg.matchCount += a.matchCount;
                agg.totalGoals += a.totalGoals;
                agg.totalShots += a.totalShots;
                agg.totalXg += a.totalXg;
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
            for (V24MatchEvent shot : r.timeline().shotEvents()) {
                if (shot.shotCoordinate() != null) {
                    shotLocationCounts.merge(shot.shotCoordinate().location(), 1L, Long::sum);
                }
            }
        }

        void printSummary(String label, double expectedIntensity) {
            double avgGoals = (double) totalGoals / matchCount;
            double avgShots = (double) totalShots / matchCount;
            double avgXg = totalXg / matchCount;
            double xgPerShot = totalShots > 0 ? totalXg / totalShots : 0;
            double convRate = totalShots > 0 ? (double) totalGoals / totalShots : 0;

            System.out.printf("--- %s (N=%d, intensity~%.2f) ---%n", label, matchCount, expectedIntensity);
            System.out.printf("  Goals:      %.2f avg%n", avgGoals);
            System.out.printf("  Shots:      %.2f avg (xG/shot=%.3f)%n", avgShots, xgPerShot);
            System.out.printf("  Conversion: %.1f%% (goals/shots)%n", convRate * 100);
            long locTotal = shotLocationCounts.values().stream().mapToLong(Long::longValue).sum();
            System.out.print("  Shot location: ");
            for (V24ShotLocation loc : V24ShotLocation.values()) {
                long count = shotLocationCounts.get(loc);
                double pct = locTotal > 0 ? 100.0 * count / locTotal : 0;
                System.out.printf("%s=%.0f%% ", loc, pct);
            }
            System.out.println();
        }
    }

    private PerScenarioMetrics runScenario(V24DetailedMatchEngine engine,
                                           List<SessionPlayer> homeStart, String homeName,
                                           List<SessionPlayer> awayStart, String awayName,
                                           String label, int n) {
        PerScenarioMetrics metrics = new PerScenarioMetrics();
        for (int seed = 1; seed <= n; seed++) {
            V24MatchContext ctx = buildContext(homeStart, homeName, awayStart, awayName, seed);
            V24DetailedMatchResult result = engine.simulate(ctx, seed);
            metrics.add(result);
        }
        return metrics;
    }

    private V24MatchContext buildContext(List<SessionPlayer> homeStart, String homeName,
                                         List<SessionPlayer> awayStart, String awayName, int seed) {
        SessionTeam homeTeam = makeTeam("home-v31c-" + seed, homeName);
        SessionTeam awayTeam = makeTeam("away-v31c-" + seed, awayName);
        return new V24MatchContext(
                "v31c-" + homeName + "-vs-" + awayName + "-" + seed,
                homeTeam.getSessionTeamId(), awayTeam.getSessionTeamId(),
                homeTeam, awayTeam,
                homeStart, awayStart,
                List.of(), List.of(),
                "4-3-3", "4-3-3",
                TeamStyle.BALANCED, TeamStyle.BALANCED);
    }

    private void printRoster(List<SessionPlayer> roster) {
        for (SessionPlayer p : roster) {
            Map<PlayerSkill, Integer> skills = p.getSkillLevels();
            Integer ovr = p.calculateOverall();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("  %-22s %-5s OVR=%-3s  ",
                    p.getName(), p.getPosition(), ovr == null ? "?" : ovr.toString()));
            appendSkill(sb, skills, PlayerSkill.WALL, "WALL");
            appendSkill(sb, skills, PlayerSkill.MARKER, "MARKER");
            appendSkill(sb, skills, PlayerSkill.PASSER, "PASSER");
            appendSkill(sb, skills, PlayerSkill.DRIBBLER, "DRIBBLER");
            appendSkill(sb, skills, PlayerSkill.SHOOTER, "SHOOTER");
            appendSkill(sb, skills, PlayerSkill.SPEEDSTER, "SPEEDSTER");
            appendSkill(sb, skills, PlayerSkill.PLAYMAKER, "PLAYMAKER");
            appendSkill(sb, skills, PlayerSkill.HEADER, "HEADER");
            appendSkill(sb, skills, PlayerSkill.TACKLER, "TACKLER");
            if (p.getHeightCm() != null) sb.append("h=").append(p.getHeightCm()).append("cm");
            System.out.println(sb);
        }
    }

    private void appendSkill(StringBuilder sb, Map<PlayerSkill, Integer> skills,
                              PlayerSkill key, String label) {
        Integer value = skills.get(key);
        if (value != null && value > 0) {
            sb.append(label).append("=").append(value).append(" ");
        }
    }

    /**
     * Load the LaLiga seed JSON from classpath.
     */
    private LaLigaSeedData loadSeed() {
        try {
            ClassPathResource resource = new ClassPathResource("seed/laliga-2024-25.json");
            ObjectMapper mapper = new ObjectMapper();
            try (var in = resource.getInputStream()) {
                return mapper.readValue(in, LaLigaSeedData.class);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load laliga-2024-25.json seed", e);
        }
    }

    /**
     * Build starting 11 for a team from seed: pick top-rated per position slot
     * (1 GK + 4 DEF + 3 MID + 2 WINGER + 1 ATT for 4-3-3, or adapt to formation).
     */
    private List<SessionPlayer> buildStarting11(LaLigaSeedData seed, String teamName) {
        List<LaLigaSeedData.PlayerDto> teamPlayers = seed.players().stream()
                .filter(p -> p.team().equalsIgnoreCase(teamName))
                .toList();

        Map<String, List<LaLigaSeedData.PlayerDto>> byPosition = new java.util.HashMap<>();
        for (LaLigaSeedData.PlayerDto p : teamPlayers) {
            byPosition.computeIfAbsent(p.position(), k -> new ArrayList<>()).add(p);
        }
        byPosition.forEach((pos, list) -> list.sort((a, b) ->
                Integer.compare(ovrOf(b), ovrOf(a))));

        List<SessionPlayer> starting = new ArrayList<>();
        starting.add(toSessionPlayer(pickTop(byPosition, "GK", 0)));
        for (int i = 0; i < 4; i++) starting.add(toSessionPlayer(pickTop(byPosition, "DEF", i)));
        for (int i = 0; i < 3; i++) starting.add(toSessionPlayer(pickTop(byPosition, "MID", i)));
        for (int i = 0; i < 2; i++) starting.add(toSessionPlayer(pickTop(byPosition, "WINGER", i)));
        starting.add(toSessionPlayer(pickTop(byPosition, "ATT", 0)));
        return starting;
    }

    private int ovrOf(LaLigaSeedData.PlayerDto p) {
        return (p.baseAttack() + p.baseDefense() + p.baseTechnique() +
                p.baseSpeed() + p.baseStamina() + p.baseMentality()) / 6;
    }

    private LaLigaSeedData.PlayerDto pickTop(Map<String, List<LaLigaSeedData.PlayerDto>> byPosition,
                                              String position, int index) {
        List<LaLigaSeedData.PlayerDto> list = byPosition.get(position);
        if (list == null || list.isEmpty()) {
            throw new IllegalStateException("No players for position " + position);
        }
        if (index >= list.size()) {
            return list.get(list.size() - 1);
        }
        return list.get(index);
    }

    /**
     * Convert a LaLigaSeedData.PlayerDto to a SessionPlayer with full attrs,
     * height, and skill levels. Mirrors runtime's conversion path.
     */
    private SessionPlayer toSessionPlayer(LaLigaSeedData.PlayerDto dto) {
        String id = "v31c-" + dto.team().replaceAll("\\s+", "") + "-" + dto.name().replaceAll("\\s+", "");
        SessionPlayer p = SessionPlayer.custom(
                id, dto.age(), dto.position(),
                dto.baseAttack(), dto.baseDefense(), dto.baseTechnique(),
                dto.baseSpeed(), dto.baseStamina(), dto.baseMentality(),
                BigDecimal.valueOf(ovrOf(dto) * 1000));
        if (dto.heightCm() != null) {
            p.setHeightCm(dto.heightCm());
        }
        if (dto.skillLevels() != null) {
            for (Map.Entry<PlayerSkill, Integer> e : dto.skillLevels().entrySet()) {
                p.setSkillLevel(e.getKey(), e.getValue());
            }
        }
        return p;
    }

    private SessionTeam makeTeam(String id, String name) {
        return SessionTeam.fromRealTeam(
                UUID.nameUUIDFromBytes(id.getBytes()),
                "world_" + id, name, "Country",
                BigDecimal.ZERO, "4-3-3", null);
    }
}
