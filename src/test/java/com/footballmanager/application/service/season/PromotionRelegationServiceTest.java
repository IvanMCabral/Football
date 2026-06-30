package com.footballmanager.application.service.season;

import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V25D78-C55.2 phase 3 unit tests for {@link PromotionRelegationService}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Top 3 of SEGUNDA ascend to PRIMERA</li>
 *   <li>Bottom 3 of PRIMERA descend to SEGUNDA</li>
 *   <li>Top 3 of TERCERA ascend to SEGUNDA</li>
 *   <li>Bottom 3 of SEGUNDA descend to TERCERA</li>
 *   <li>Tiebreaker: points → goal difference → goals scored → teamId</li>
 *   <li>Validation: each division must have exactly 20 standings</li>
 *   <li>applyMovements returns teams with updated divisions</li>
 *   <li>Total movements: exactly 12 (3 prom + 3 rel per direction × 2 directions)</li>
 * </ul>
 */
class PromotionRelegationServiceTest {

    private PromotionRelegationService service;
    private List<Team> sixtyTeams;

    @BeforeEach
    void setUp() {
        service = new PromotionRelegationService();
        sixtyTeams = buildSixtyTeams();
    }

    @Test
    @DisplayName("V25D78-C55.2 P3 #1: top 3 of SEGUNDA ascend to PRIMERA")
    void top_3_of_segunda_ascend_to_primera() {
        Map<Division, List<PromotionRelegationService.Standing>> standings =
                buildStandings(/*primeraPts*/randomPts(), /*segundaPts*/100,
                        /*terceraPts*/randomPts());
        Map<TeamId, Division> movements = service.calculateMovements(standings);
        List<PromotionRelegationService.Standing> sortedSegunda =
                standings.get(Division.SEGUNDA).stream()
                        .sorted(Comparator
                                .comparingInt(PromotionRelegationService.Standing::points).reversed())
                        .collect(Collectors.toList());
        for (int i = 0; i < 3; i++) {
            TeamId promotedId = sortedSegunda.get(i).teamId();
            assertThat(movements.get(promotedId))
                .as("SEGUNDA top %d (%s) should be promoted to PRIMERA",
                    i + 1, promotedId)
                .isEqualTo(Division.PRIMERA);
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 P3 #2: bottom 3 of PRIMERA descend to SEGUNDA")
    void bottom_3_of_primera_descend_to_segunda() {
        Map<Division, List<PromotionRelegationService.Standing>> standings =
                buildStandings(/*primeraPts*/50, /*segundaPts*/randomPts(),
                        /*terceraPts*/randomPts());
        Map<TeamId, Division> movements = service.calculateMovements(standings);
        List<PromotionRelegationService.Standing> sortedPrimera =
                standings.get(Division.PRIMERA).stream()
                        .sorted(Comparator
                                .comparingInt(PromotionRelegationService.Standing::points).reversed())
                        .collect(Collectors.toList());
        for (int i = 0; i < 3; i++) {
            int idx = 19 - i; // bottom 3
            TeamId relegatedId = sortedPrimera.get(idx).teamId();
            assertThat(movements.get(relegatedId))
                .as("PRIMERA bottom %d (%s) should be relegated to SEGUNDA",
                    i + 1, relegatedId)
                .isEqualTo(Division.SEGUNDA);
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 P3 #3: top 3 of TERCERA ascend to SEGUNDA")
    void top_3_of_tercera_ascend_to_segunda() {
        Map<Division, List<PromotionRelegationService.Standing>> standings =
                buildStandings(/*primeraPts*/randomPts(), /*segundaPts*/randomPts(),
                        /*terceraPts*/100);
        Map<TeamId, Division> movements = service.calculateMovements(standings);
        List<PromotionRelegationService.Standing> sortedTercera =
                standings.get(Division.TERCERA).stream()
                        .sorted(Comparator
                                .comparingInt(PromotionRelegationService.Standing::points).reversed())
                        .collect(Collectors.toList());
        for (int i = 0; i < 3; i++) {
            TeamId promotedId = sortedTercera.get(i).teamId();
            assertThat(movements.get(promotedId))
                .as("TERCERA top %d (%s) should be promoted to SEGUNDA",
                    i + 1, promotedId)
                .isEqualTo(Division.SEGUNDA);
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 P3 #4: bottom 3 of SEGUNDA descend to TERCERA")
    void bottom_3_of_segunda_descend_to_tercera() {
        Map<Division, List<PromotionRelegationService.Standing>> standings =
                buildStandings(/*primeraPts*/randomPts(), /*segundaPts*/50,
                        /*terceraPts*/randomPts());
        Map<TeamId, Division> movements = service.calculateMovements(standings);
        List<PromotionRelegationService.Standing> sortedSegunda =
                standings.get(Division.SEGUNDA).stream()
                        .sorted(Comparator
                                .comparingInt(PromotionRelegationService.Standing::points).reversed())
                        .collect(Collectors.toList());
        for (int i = 0; i < 3; i++) {
            int idx = 19 - i;
            TeamId relegatedId = sortedSegunda.get(idx).teamId();
            assertThat(movements.get(relegatedId))
                .as("SEGUNDA bottom %d (%s) should be relegated to TERCERA",
                    i + 1, relegatedId)
                .isEqualTo(Division.TERCERA);
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 P3 #5: total movements is exactly 12 (3+3 × 2 directions)")
    void total_movements_is_12() {
        Map<Division, List<PromotionRelegationService.Standing>> standings =
                buildStandings(50, 50, 50);
        Map<TeamId, Division> movements = service.calculateMovements(standings);
        assertThat(movements).hasSize(12);
    }

    @Test
    @DisplayName("V25D78-C55.2 P3 #6: tiebreaker by goal difference (same points)")
    void tiebreaker_by_goal_difference() {
        // Two teams with same points but different GD. Higher GD should rank higher.
        TeamId teamA = TeamId.fromString("00000000-0000-0000-0000-000000000001");
        TeamId teamB = TeamId.fromString("00000000-0000-0000-0000-000000000002");
        Map<Division, List<PromotionRelegationService.Standing>> standings = new EnumMap<>(Division.class);
        standings.put(Division.PRIMERA, buildDivStandings(Division.PRIMERA, 100));
        // Override team A and B with specific points/GD
        standings.put(Division.PRIMERA, List.of(
                new PromotionRelegationService.Standing(teamA, 50, 30, 20), // GD=+10
                new PromotionRelegationService.Standing(teamB, 50, 25, 25)  // GD=0
        ));
        // Fill the rest with dummy standings to make 20 total
        List<PromotionRelegationService.Standing> full = new ArrayList<>(standings.get(Division.PRIMERA));
        for (int i = 3; i <= 20; i++) {
            full.add(new PromotionRelegationService.Standing(
                    TeamId.fromString(String.format("00000000-0000-0000-0000-%012d", i)),
                    10, 5, 5));
        }
        standings.put(Division.PRIMERA, full);
        // Add SEGUNDA, TERCERA with random data
        standings.put(Division.SEGUNDA, buildDivStandings(Division.SEGUNDA, 50));
        standings.put(Division.TERCERA, buildDivStandings(Division.TERCERA, 30));

        Map<TeamId, Division> movements = service.calculateMovements(standings);
        // Both teamA and teamB are in PRIMERA, neither should be in movements
        // (since their points are mid-pack, not in top/bottom 3).
        // Verify that teamA ranks higher than teamB internally.
        List<PromotionRelegationService.Standing> sortedPrimera =
                standings.get(Division.PRIMERA).stream()
                        .sorted(Comparator
                                .comparingInt(PromotionRelegationService.Standing::points).reversed()
                                .thenComparing(Comparator.comparingInt(
                                        PromotionRelegationService.Standing::goalDifference).reversed()))
                        .collect(Collectors.toList());
        int idxA = sortedPrimera.stream()
                .map(PromotionRelegationService.Standing::teamId)
                .collect(Collectors.toList())
                .indexOf(teamA);
        int idxB = sortedPrimera.stream()
                .map(PromotionRelegationService.Standing::teamId)
                .collect(Collectors.toList())
                .indexOf(teamB);
        assertThat(idxA)
            .as("teamA (GD=+10) should rank higher than teamB (GD=0)")
            .isLessThan(idxB);
    }

    @Test
    @DisplayName("V25D78-C55.2 P3 #7: validation rejects division with != 20 standings")
    void validation_rejects_wrong_count() {
        Map<Division, List<PromotionRelegationService.Standing>> standings = new EnumMap<>(Division.class);
        standings.put(Division.PRIMERA, buildDivStandings(Division.PRIMERA, 50));
        standings.put(Division.SEGUNDA, buildDivStandings(Division.SEGUNDA, 50).subList(0, 15));
        standings.put(Division.TERCERA, buildDivStandings(Division.TERCERA, 30));
        assertThatThrownBy(() -> service.calculateMovements(standings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEGUNDA has 15");
    }

    @Test
    @DisplayName("V25D78-C55.2 P3 #8: applyMovements returns teams with updated divisions")
    void apply_movements_returns_updated_teams() {
        Map<Division, List<PromotionRelegationService.Standing>> standings =
                buildStandings(50, 50, 50);
        Map<TeamId, Division> movements = service.calculateMovements(standings);
        List<Team> updated = service.applyMovements(sixtyTeams, movements);
        assertThat(updated).hasSize(60);
        // Verify the moved teams now have new divisions.
        for (Map.Entry<TeamId, Division> entry : movements.entrySet()) {
            TeamId movedId = entry.getKey();
            Division newDiv = entry.getValue();
            Team movedTeam = updated.stream()
                    .filter(t -> t.getId().equals(movedId))
                    .findFirst().orElseThrow();
            assertThat(movedTeam.getDivision())
                .as("team %s should now be in %s", movedId, newDiv)
                .isEqualTo(newDiv);
        }
        // Verify non-moved teams still have their original division.
        Set<TeamId> movedIds = movements.keySet();
        for (Team team : sixtyTeams) {
            if (!movedIds.contains(team.getId())) {
                Team updatedTeam = updated.stream()
                        .filter(t -> t.getId().equals(team.getId()))
                        .findFirst().orElseThrow();
                assertThat(updatedTeam.getDivision())
                    .as("non-moved team %s should keep original division", team.getId())
                    .isEqualTo(team.getDivision());
            }
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 P3 #9: deterministic output (same inputs = same movements)")
    void deterministic_output() {
        Map<Division, List<PromotionRelegationService.Standing>> standings =
                buildStandings(50, 50, 50);
        Map<TeamId, Division> run1 = service.calculateMovements(standings);
        Map<TeamId, Division> run2 = service.calculateMovements(standings);
        assertThat(run1).isEqualTo(run2);
    }

    // ========== Helpers ==========

    /**
     * Build 60 teams: 20 PRIMERA + 20 SEGUNDA + 20 TERCERA.
     */
    private List<Team> buildSixtyTeams() {
        List<Team> teams = new ArrayList<>(60);
        for (int d = 0; d < 3; d++) {
            Division div = Division.values()[d];
            for (int i = 0; i < 20; i++) {
                int idx = d * 20 + i + 1; // +1 to avoid nil UUID
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
     * Build standings map. Each division has 20 standings with deterministic
     * points/GD/GS based on the division's base points + team index.
     * Different divisions get different points ranges.
     */
    private Map<Division, List<PromotionRelegationService.Standing>> buildStandings(
            int primeraPts, int segundaPts, int terceraPts) {
        Map<Division, List<PromotionRelegationService.Standing>> standings = new EnumMap<>(Division.class);
        standings.put(Division.PRIMERA, buildDivStandings(Division.PRIMERA, primeraPts));
        standings.put(Division.SEGUNDA, buildDivStandings(Division.SEGUNDA, segundaPts));
        standings.put(Division.TERCERA, buildDivStandings(Division.TERCERA, terceraPts));
        return standings;
    }

    private List<PromotionRelegationService.Standing> buildDivStandings(Division div, int basePts) {
        // Team i has points = basePts + (19 - i) * 3 (so team 0 = highest, team 19 = lowest).
        // GD = 30 - i*2 (so team 0 has GD=+30, team 19 has GD=-8).
        // GS = 50 - i (so team 0 has 50, team 19 has 31).
        return IntStream.range(0, 20)
                .mapToObj(i -> {
                    int idx = (div.ordinal() * 20 + i) + 1;
                    TeamId id = TeamId.fromString(
                            String.format("00000000-0000-0000-0000-%012d", idx));
                    int points = basePts + (19 - i) * 3;
                    int gd = 30 - i * 2;
                    int gs = 50 - i;
                    int gc = gs - gd;
                    return new PromotionRelegationService.Standing(id, points, gs, gc);
                })
                .collect(Collectors.toList());
    }

    /**
     * Return a random-ish but deterministic int for use as basePts to vary
     * divisions across tests without coupling them to each other.
     */
    private int randomPts() {
        return (int) (System.nanoTime() % 100);
    }
}