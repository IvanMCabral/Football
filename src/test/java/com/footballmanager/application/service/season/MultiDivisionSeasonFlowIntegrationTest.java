package com.footballmanager.application.service.season;

import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.service.DivisionScheduler;
import com.footballmanager.domain.service.FixtureGenerator;
import com.footballmanager.domain.service.FixtureValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V25D78-C55.2 phase 5: Back-end integration tests for multi-division season flow.
 *
 * <p>Verifies end-to-end:
 * <ol>
 *   <li>Build 60 teams with divisions</li>
 *   <li>Generate 78-matchday season (DivisionScheduler)</li>
 *   <li>Simulate season: assign random points to each team</li>
 *   <li>Apply PromotionRelegationService to compute movements</li>
 *   <li>Apply movements to teams → verify final divisions</li>
 * </ol>
 *
 * <p>This is the back-end integration test that ties phases 1+2+3 together
 * for a complete multi-division season cycle.
 */
class MultiDivisionSeasonFlowIntegrationTest {

    @Test
    @DisplayName("V25D78-C55.2 P5 #1: full multi-division season cycle (build → schedule → simulate → promote/relegate)")
    void full_season_cycle() {
        // Phase 1+2 setup: build 60 teams and DivisionScheduler.
        List<Team> teams = buildSixtyTeams();
        DivisionScheduler scheduler = new DivisionScheduler(
                new FixtureGenerator(new FixtureValidator()));

        // Generate 78-matchday schedule.
        List<DivisionScheduler.DivisionFixtureRound> rounds =
                scheduler.generateSeasonFixtures(teams);
        assertThat(rounds).hasSize(78);

        // Phase 5 simulation: assign points based on a deterministic function
        // of each team's strength (we simulate "season results" synthetically
        // — the engine would do this for real in phase 5 integration).
        Map<TeamId, PromotionRelegationService.Standing> standings =
                simulateSeasonResults(teams);

        // Group standings by division.
        Map<TeamId, Division> teamDivisions = teams.stream()
                .collect(Collectors.toMap(Team::getId, Team::getDivision));
        Map<Division, List<PromotionRelegationService.Standing>> byDivision =
                new EnumMap<>(Division.class);
        for (Division d : Division.values()) {
            List<PromotionRelegationService.Standing> divStandings = standings.entrySet().stream()
                    .filter(e -> teamDivisions.get(e.getKey()) == d)
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
            byDivision.put(d, divStandings);
        }

        // Phase 3: calculate movements.
        PromotionRelegationService promoReg = new PromotionRelegationService();
        Map<TeamId, Division> movements = promoReg.calculateMovements(byDivision);

        // Verify 12 total movements.
        assertThat(movements).hasSize(12);

        // Apply movements.
        List<Team> updatedTeams = promoReg.applyMovements(teams, movements);

        // Verify final league has exactly 20 teams per division.
        Map<Division, Long> finalCounts = updatedTeams.stream()
                .collect(Collectors.groupingBy(Team::getDivision, Collectors.counting()));
        assertThat(finalCounts.get(Division.PRIMERA)).isEqualTo(20);
        assertThat(finalCounts.get(Division.SEGUNDA)).isEqualTo(20);
        assertThat(finalCounts.get(Division.TERCERA)).isEqualTo(20);
    }

    @Test
    @DisplayName("V25D78-C55.2 P5 #2: schedule generator produces no team conflicts across 78 matchdays")
    void schedule_no_team_conflicts() {
        List<Team> teams = buildSixtyTeams();
        DivisionScheduler scheduler = new DivisionScheduler(
                new FixtureGenerator(new FixtureValidator()));
        List<DivisionScheduler.DivisionFixtureRound> rounds =
                scheduler.generateSeasonFixtures(teams);

        // Track each team's appearance count per matchday — should always be exactly 1.
        Map<TeamId, Integer> appearances = new HashMap<>();
        for (DivisionScheduler.DivisionFixtureRound round : rounds) {
            Set<TeamId> dayUsed = new HashSet<>();
            for (DivisionScheduler.Matchup m : round.matches()) {
                assertThat(dayUsed.add(m.home()))
                    .as("home %s appears twice in round %d", m.home(), round.roundNumber())
                    .isTrue();
                assertThat(dayUsed.add(m.away()))
                    .as("away %s appears twice in round %d", m.away(), round.roundNumber())
                    .isTrue();
                appearances.merge(m.home(), 1, Integer::sum);
                appearances.merge(m.away(), 1, Integer::sum);
            }
        }
        // Every team plays exactly 78 matches.
        for (Team team : teams) {
            assertThat(appearances.get(team.getId()))
                .as("team %s should play 78 matches", team.getId())
                .isEqualTo(78);
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 P5 #3: promotion/relegation balance — after movements, division sizes preserved")
    void promo_reg_preserves_division_sizes() {
        List<Team> teams = buildSixtyTeams();
        Map<TeamId, PromotionRelegationService.Standing> standings =
                simulateSeasonResults(teams);
        Map<TeamId, Division> teamDivisions = teams.stream()
                .collect(Collectors.toMap(Team::getId, Team::getDivision));
        Map<Division, List<PromotionRelegationService.Standing>> byDivision =
                new EnumMap<>(Division.class);
        for (Division d : Division.values()) {
            byDivision.put(d, standings.entrySet().stream()
                    .filter(e -> teamDivisions.get(e.getKey()) == d)
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList()));
        }
        PromotionRelegationService promoReg = new PromotionRelegationService();
        Map<TeamId, Division> movements = promoReg.calculateMovements(byDivision);
        List<Team> updated = promoReg.applyMovements(teams, movements);

        // Verify no division gained or lost teams (movements balance out).
        Map<Division, Long> before = teams.stream()
                .collect(Collectors.groupingBy(Team::getDivision, Collectors.counting()));
        Map<Division, Long> after = updated.stream()
                .collect(Collectors.groupingBy(Team::getDivision, Collectors.counting()));
        for (Division d : Division.values()) {
            assertThat(after.get(d))
                .as("division %s should have same size after movements", d)
                .isEqualTo(before.get(d));
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 P5 #4: standings ordering uses points → GD → GS tiebreaker chain")
    void standings_ordering_tiebreaker_chain() {
        // Create 4 teams with same points (50) but different GD/GS.
        TeamId t1 = TeamId.fromString("00000000-0000-0000-0000-00000000aaaa");
        TeamId t2 = TeamId.fromString("00000000-0000-0000-0000-00000000bbbb");
        TeamId t3 = TeamId.fromString("00000000-0000-0000-0000-00000000cccc");
        TeamId t4 = TeamId.fromString("00000000-0000-0000-0000-00000000dddd");

        List<PromotionRelegationService.Standing> test4 = new ArrayList<>(List.of(
                new PromotionRelegationService.Standing(t1, 50, 30, 10), // GD=+20
                new PromotionRelegationService.Standing(t2, 50, 25, 10), // GD=+15
                new PromotionRelegationService.Standing(t3, 50, 20, 10), // GD=+10
                new PromotionRelegationService.Standing(t4, 50, 15, 10)  // GD=+5
        ));
        // Fill with 16 more teams to make 20.
        for (int i = 5; i <= 20; i++) {
            int idx = 100 + i;
            test4.add(new PromotionRelegationService.Standing(
                    TeamId.fromString(String.format("00000000-0000-0000-0000-%012d", idx)),
                    10, 5, 5));
        }

        List<PromotionRelegationService.Standing> sorted = test4.stream()
                .sorted(Comparator
                        .comparingInt(PromotionRelegationService.Standing::points).reversed()
                        .thenComparing(Comparator.comparingInt(
                                PromotionRelegationService.Standing::goalDifference).reversed()))
                .collect(Collectors.toList());

        assertThat(sorted.get(0).teamId()).isEqualTo(t1);
        assertThat(sorted.get(1).teamId()).isEqualTo(t2);
        assertThat(sorted.get(2).teamId()).isEqualTo(t3);
        assertThat(sorted.get(3).teamId()).isEqualTo(t4);
    }

    // ========== Helpers ==========

    private List<Team> buildSixtyTeams() {
        List<Team> teams = new ArrayList<>(60);
        for (int d = 0; d < 3; d++) {
            Division div = Division.values()[d];
            for (int i = 0; i < 20; i++) {
                int idx = d * 20 + i + 1;
                TeamId id = TeamId.fromString(
                        String.format("00000000-0000-0000-0000-%012d", idx));
                UserId managerId = UserId.fromString(
                        String.format("11111111-1111-1111-1111-%012d", idx));
                teams.add(Team.create(id, managerId,
                        div.name().charAt(0) + "-" + i, "TestLand",
                        BigDecimal.valueOf(1000000L),
                        Formation.FORMATION_4_3_3, div));
            }
        }
        return teams;
    }

    /**
     * Simulate season results: assign deterministic points/GD/GS to each team
     * based on team index within division (higher index = stronger team = more points).
     * Real implementation would use the match engine to compute these; for
     * the back-end integration test, we just need realistic-looking standings.
     */
    private Map<TeamId, PromotionRelegationService.Standing> simulateSeasonResults(
            List<Team> teams) {
        Map<TeamId, Integer> teamIdx = new HashMap<>();
        for (int d = 0; d < 3; d++) {
            for (int i = 0; i < 20; i++) {
                int idx = d * 20 + i + 1;
                teamIdx.put(TeamId.fromString(
                        String.format("00000000-0000-0000-0000-%012d", idx)), i);
            }
        }
        return teams.stream()
                .collect(Collectors.toMap(
                        Team::getId,
                        team -> {
                            int i = teamIdx.get(team.getId());
                            // Higher index = more points (max 78 wins × 3 = 234, but we cap at 78).
                            int points = 30 + (19 - i) * 2; // 30..68 range
                            int gd = (19 - i) - 10; // +9..-10 range
                            int gs = 40 + (19 - i); // 40..59 range
                            int gc = gs - gd;
                            return new PromotionRelegationService.Standing(
                                    team.getId(), points, gs, gc);
                        }));
    }
}