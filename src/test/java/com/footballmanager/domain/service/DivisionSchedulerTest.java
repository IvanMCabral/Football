package com.footballmanager.domain.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V25D78-C55.2 phase 2 unit tests for {@link DivisionScheduler}.
 *
 * <p>Verifies the multi-division 78-matchday schedule generation:
 * <ul>
 *   <li>38 intra-division matchdays (ida + vuelta, 10 matches per division per day)</li>
 *   <li>40 cross-division matchdays (P↔S + P↔T + S↔T, 30 matches per day)</li>
 *   <li>Total 78 matchdays, 2,340 matches (per team: 78)</li>
 *   <li>Each team plays exactly once per matchday (no overlaps)</li>
 *   <li>All 1,200 unique cross-division matchups appear exactly once</li>
 *   <li>Validation rejects non-60-team lists or wrong division counts</li>
 * </ul>
 */
class DivisionSchedulerTest {

    private DivisionScheduler scheduler;
    private List<Team> sixtyTeams;

    @BeforeEach
    void setUp() {
        FixtureGenerator flat = new FixtureGenerator(new FixtureValidator());
        scheduler = new DivisionScheduler(flat);
        sixtyTeams = buildSixtyTeams();
    }

    @Test
    @DisplayName("V25D78-C55.2 #1: 60-team league produces 78 matchdays total (38 intra + 40 cross)")
    void generateSeason_produces_78_matchdays() {
        List<DivisionScheduler.DivisionFixtureRound> rounds = scheduler.generateSeasonFixtures(sixtyTeams);
        assertThat(rounds).hasSize(DivisionScheduler.TOTAL_MATCHDAYS);
        assertThat(rounds.get(0).roundNumber()).isEqualTo(1);
        assertThat(rounds.get(77).roundNumber()).isEqualTo(78);
    }

    @Test
    @DisplayName("V25D78-C55.2 #2: first 38 matchdays are INTRA (round-robin ida+vuelta per division)")
    void intra_matchdays_have_30_matches_per_day() {
        List<DivisionScheduler.DivisionFixtureRound> rounds = scheduler.generateSeasonFixtures(sixtyTeams);
        for (int i = 0; i < DivisionScheduler.INTRA_MATCHDAYS; i++) {
            DivisionScheduler.DivisionFixtureRound round = rounds.get(i);
            assertThat(round.kind()).isEqualTo(DivisionScheduler.DivisionFixtureRound.Kind.INTRA);
            assertThat(round.size()).isEqualTo(30); // 60 teams / 2
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 #3: last 40 matchdays are CROSS (cross-division single round)")
    void cross_matchdays_have_30_matches_per_day() {
        List<DivisionScheduler.DivisionFixtureRound> rounds = scheduler.generateSeasonFixtures(sixtyTeams);
        for (int i = DivisionScheduler.INTRA_MATCHDAYS;
             i < DivisionScheduler.TOTAL_MATCHDAYS; i++) {
            DivisionScheduler.DivisionFixtureRound round = rounds.get(i);
            assertThat(round.kind()).isEqualTo(DivisionScheduler.DivisionFixtureRound.Kind.CROSS);
            assertThat(round.size()).isEqualTo(30);
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 #4: no team plays twice in the same matchday (no overlaps)")
    void no_team_plays_twice_in_same_matchday() {
        List<DivisionScheduler.DivisionFixtureRound> rounds = scheduler.generateSeasonFixtures(sixtyTeams);
        for (DivisionScheduler.DivisionFixtureRound round : rounds) {
            Set<TeamId> played = new HashSet<>();
            for (DivisionScheduler.Matchup m : round.matches()) {
                assertThat(played.add(m.home()))
                    .as("home %s appears twice in round %d", m.home(), round.roundNumber())
                    .isTrue();
                assertThat(played.add(m.away()))
                    .as("away %s appears twice in round %d", m.away(), round.roundNumber())
                    .isTrue();
                assertThat(m.home()).isNotEqualTo(m.away());
            }
            assertThat(played).hasSize(60);
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 #5: each team plays 38 intra matches + 40 cross matches = 78 total")
    void each_team_plays_78_matches_total() {
        List<DivisionScheduler.DivisionFixtureRound> rounds = scheduler.generateSeasonFixtures(sixtyTeams);
        Map<TeamId, Integer> appearanceCount = new HashMap<>();
        Map<TeamId, Integer> intraCount = new HashMap<>();
        Map<TeamId, Integer> crossCount = new HashMap<>();
        for (DivisionScheduler.DivisionFixtureRound round : rounds) {
            for (DivisionScheduler.Matchup m : round.matches()) {
                appearanceCount.merge(m.home(), 1, Integer::sum);
                appearanceCount.merge(m.away(), 1, Integer::sum);
                if (round.kind() == DivisionScheduler.DivisionFixtureRound.Kind.INTRA) {
                    intraCount.merge(m.home(), 1, Integer::sum);
                    intraCount.merge(m.away(), 1, Integer::sum);
                } else {
                    crossCount.merge(m.home(), 1, Integer::sum);
                    crossCount.merge(m.away(), 1, Integer::sum);
                }
            }
        }
        for (TeamId teamId : appearanceCount.keySet()) {
            assertThat(appearanceCount.get(teamId))
                .as("total matches for %s", teamId).isEqualTo(78);
            assertThat(intraCount.get(teamId))
                .as("intra matches for %s", teamId).isEqualTo(38);
            assertThat(crossCount.get(teamId))
                .as("cross matches for %s", teamId).isEqualTo(40);
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 #6: all 1,200 unique cross-division matchups appear exactly once")
    void all_cross_matchups_appear_exactly_once() {
        List<DivisionScheduler.DivisionFixtureRound> rounds = scheduler.generateSeasonFixtures(sixtyTeams);
        Map<TeamId, Division> teamDivisions = sixtyTeams.stream()
                .collect(Collectors.toMap(Team::getId, Team::getDivision));

        Set<String> uniqueMatchups = new HashSet<>();
        Map<String, Integer> matchupCounts = new HashMap<>();
        for (DivisionScheduler.DivisionFixtureRound round : rounds) {
            if (round.kind() != DivisionScheduler.DivisionFixtureRound.Kind.CROSS) continue;
            for (DivisionScheduler.Matchup m : round.matches()) {
                String key = matchupKey(m);
                matchupCounts.merge(key, 1, Integer::sum);
                uniqueMatchups.add(key);
            }
        }
        // 1200 cross matchups = 20 P x 20 S + 20 P x 20 T + 20 S x 20 T
        assertThat(uniqueMatchups).hasSize(1200);
        for (Map.Entry<String, Integer> entry : matchupCounts.entrySet()) {
            assertThat(entry.getValue())
                .as("cross matchup %s appears %d times (expected 1)",
                    entry.getKey(), entry.getValue())
                .isEqualTo(1);
        }
        // Sanity check: all matchups are cross-division.
        for (String key : uniqueMatchups) {
            String[] ids = key.split(" vs ");
            Division d1 = teamDivisions.get(TeamId.fromString(ids[0]));
            Division d2 = teamDivisions.get(TeamId.fromString(ids[1]));
            assertThat(d1).as("division of first team in %s", key).isNotEqualTo(d2);
        }
    }

    @Test
    @DisplayName("V25D78-C55.2 #7: intra-division matchdays have 10 matches per division per day")
    void intra_matchdays_have_10_matches_per_division() {
        List<DivisionScheduler.DivisionFixtureRound> rounds = scheduler.generateSeasonFixtures(sixtyTeams);
        Map<TeamId, Division> teamDivisions = sixtyTeams.stream()
                .collect(Collectors.toMap(Team::getId, Team::getDivision));

        // Check first intra matchday: should have 10 PRIMERA + 10 SEGUNDA + 10 TERCERA matches.
        DivisionScheduler.DivisionFixtureRound firstIntra = rounds.get(0);
        Map<Division, Integer> divisionMatchCount = new EnumMap<>(Division.class);
        for (DivisionScheduler.Matchup m : firstIntra.matches()) {
            Division d1 = teamDivisions.get(m.home());
            Division d2 = teamDivisions.get(m.away());
            assertThat(d1).as("intra match %s must be same division", m).isEqualTo(d2);
            divisionMatchCount.merge(d1, 1, Integer::sum);
        }
        assertThat(divisionMatchCount.get(Division.PRIMERA)).isEqualTo(10);
        assertThat(divisionMatchCount.get(Division.SEGUNDA)).isEqualTo(10);
        assertThat(divisionMatchCount.get(Division.TERCERA)).isEqualTo(10);
    }

    @Test
    @DisplayName("V25D78-C55.2 #8: validation rejects non-60-team lists")
    void validate_rejects_wrong_team_count() {
        List<Team> fiftyNineTeams = sixtyTeams.subList(0, 59);
        assertThatThrownBy(() -> scheduler.generateSeasonFixtures(fiftyNineTeams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires exactly 60 teams");
    }

    @Test
    @DisplayName("V25D78-C55.2 #9: validation rejects wrong division count")
    void validate_rejects_wrong_division_count() {
        // 60 teams but 25 in PRIMERA, 20 in SEGUNDA, 15 in TERCERA
        List<Team> wrongDistribution = new ArrayList<>(sixtyTeams);
        // Move 5 PRIMERA teams to TERCERA
        List<Team> primera = wrongDistribution.stream()
                .filter(t -> t.getDivision() == Division.PRIMERA)
                .limit(5)
                .collect(Collectors.toList());
        for (Team t : primera) {
            wrongDistribution.remove(t);
            wrongDistribution.add(Team.create(t.getId(), t.getManagerId(),
                    t.getName(), t.getCountry(), t.getBudget(),
                    t.getFormation(), Division.TERCERA));
        }
        assertThatThrownBy(() -> scheduler.generateSeasonFixtures(wrongDistribution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Division PRIMERA has 15");
    }

    @Test
    @DisplayName("V25D78-C55.2 #10: isLegacyLeague correctly detects legacy vs multi-division leagues")
    void isLegacyLeague_detection() {
        assertThat(scheduler.isLegacyLeague(sixtyTeams)).isFalse();
        List<Team> legacyLaLiga20 = sixtyTeams.subList(0, 20);
        assertThat(scheduler.isLegacyLeague(legacyLaLiga20)).isTrue();
    }

    @Test
    @DisplayName("V25D78-C55.2 #11: schedule is deterministic across two consecutive runs")
    void schedule_is_deterministic() {
        List<DivisionScheduler.DivisionFixtureRound> run1 = scheduler.generateSeasonFixtures(sixtyTeams);
        List<DivisionScheduler.DivisionFixtureRound> run2 = scheduler.generateSeasonFixtures(sixtyTeams);
        assertThat(run1).hasSize(run2.size());
        for (int i = 0; i < run1.size(); i++) {
            assertThat(run1.get(i).roundNumber()).isEqualTo(run2.get(i).roundNumber());
            assertThat(run1.get(i).kind()).isEqualTo(run2.get(i).kind());
            assertThat(run1.get(i).matches()).isEqualTo(run2.get(i).matches());
        }
    }

    // ========== Helpers ==========

    /**
     * Build 60 teams: 20 PRIMERA + 20 SEGUNDA + 20 TERCERA, named by division+index.
     * Deterministic UUIDs derived from index for reproducible tests.
     * UUIDs start at 0x1000... to avoid the nil UUID (00000000-...).
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

    private String matchupKey(DivisionScheduler.Matchup m) {
        // Normalize: pair(A, B) == pair(B, A) for unordered matchup counting.
        String a = m.home().getValue().toString();
        String b = m.away().getValue().toString();
        return a.compareTo(b) < 0 ? (a + " vs " + b) : (b + " vs " + a);
    }
}