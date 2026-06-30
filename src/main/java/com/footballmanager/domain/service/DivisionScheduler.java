package com.footballmanager.domain.service;

import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.TeamId;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * V25D78-C55.2 phase 2: Division-aware fixture scheduler.
 *
 * <p>Generates the 78-matchday season schedule for a 60-team league split
 * into 3 divisions (PRIMERA / SEGUNDA / TERCERA, 20 teams each):
 * <ul>
 *   <li><b>Matchdays 1-38:</b> intra-division round-robin (ida + vuelta).
 *       Each team plays 19 other-division opponents twice (38 matches).</li>
 *   <li><b>Matchdays 39-78:</b> cross-division single round.
 *       Each team plays 40 cross-division opponents once (40 matches).
 *       Total per team: 78 matches. Total per season: 2,340 matches.</li>
 * </ul>
 *
 * <p>Cross-division scheduling: 1,200 unique cross matchups (P↔S 400 +
 * P↔T 400 + S↔T 400) distributed across 40 matchdays via greedy first-fit.
 * Each matchday is a perfect matching of all 60 teams (no team plays twice).
 *
 * <p>Legacy leagues (not 60 teams, or no division field): falls back to
 * flat round-robin via {@link FixtureGenerator} for backward-compat.
 */
@Component
public class DivisionScheduler {

    public static final int TEAMS_PER_DIVISION = 20;
    public static final int TOTAL_TEAMS = TEAMS_PER_DIVISION * 3;
    public static final int INTRA_MATCHDAYS = 38;     // ida + vuelta intra-division
    public static final int CROSS_MATCHDAYS = 40;     // cross-division single round
    public static final int TOTAL_MATCHDAYS = INTRA_MATCHDAYS + CROSS_MATCHDAYS;
    public static final int INTRA_MATCHES_PER_TEAM = 38;
    public static final int CROSS_MATCHES_PER_TEAM = 40;

    private final FixtureGenerator flatScheduler;

    public DivisionScheduler(FixtureGenerator flatScheduler) {
        this.flatScheduler = flatScheduler;
    }

    /**
     * Generate the full 78-matchday schedule. Each {@link DivisionFixtureRound}
     * contains 30 matches (all 60 teams play once).
     *
     * @param teams 60 teams split into 3 divisions of 20. Order doesn't matter.
     * @return 78 rounds, sequentially indexed from 1 to 78.
     * @throws IllegalArgumentException if any division has != 20 teams.
     */
    public List<DivisionFixtureRound> generateSeasonFixtures(List<Team> teams) {
        validateAndGroup(teams);
        Map<Division, List<Team>> byDivision = teams.stream()
                .collect(Collectors.groupingBy(Team::getDivision));

        List<DivisionFixtureRound> rounds = new ArrayList<>(TOTAL_MATCHDAYS);

        // Phase 1: intra-division round-robin (38 rounds, all 3 divisions in parallel).
        // Each round has 30 matches: 10 from each division's round-robin.
        List<Team> primera = byDivision.get(Division.PRIMERA);
        List<Team> segunda = byDivision.get(Division.SEGUNDA);
        List<Team> tercera = byDivision.get(Division.TERCERA);
        // Sort by TeamId so round-robin is deterministic across runs.
        Comparator<Team> byId = Comparator.comparing(t -> t.getId().getValue().toString());
        primera.sort(byId);
        segunda.sort(byId);
        tercera.sort(byId);

        List<List<Matchup>> intraP = intraDivisionRoundRobin(primera);
        List<List<Matchup>> intraS = intraDivisionRoundRobin(segunda);
        List<List<Matchup>> intraT = intraDivisionRoundRobin(tercera);

        for (int i = 0; i < INTRA_MATCHDAYS; i++) {
            List<Matchup> roundMatches = new ArrayList<>();
            roundMatches.addAll(intraP.get(i));
            roundMatches.addAll(intraS.get(i));
            roundMatches.addAll(intraT.get(i));
            rounds.add(new DivisionFixtureRound(i + 1, DivisionFixtureRound.Kind.INTRA,
                    roundMatches));
        }

        // Phase 2: cross-division single round (40 rounds, all 3 division pairs).
        // Constructive half-scheduling covers all 1,200 unique cross matchups.
        List<List<Matchup>> crossRounds = distributeIntoMatchdaysConstructive(
                primera, segunda, tercera);
        for (int i = 0; i < CROSS_MATCHDAYS; i++) {
            rounds.add(new DivisionFixtureRound(INTRA_MATCHDAYS + i + 1,
                    DivisionFixtureRound.Kind.CROSS, crossRounds.get(i)));
        }

        return rounds;
    }

    /**
     * Detect if a league is "legacy" (not yet configured for multi-division).
     * Legacy = no division field, OR != 20 teams per division, OR != 60 total.
     * For legacy leagues, delegate to flat round-robin.
     */
    public boolean isLegacyLeague(List<Team> teams) {
        if (teams.size() != TOTAL_TEAMS) return true;
        Map<Division, Long> counts = teams.stream()
                .collect(Collectors.groupingBy(Team::getDivision, Collectors.counting()));
        for (Division d : Division.values()) {
            if (counts.getOrDefault(d, 0L) != TEAMS_PER_DIVISION) return true;
        }
        return false;
    }

    /**
     * Generate fixtures for a legacy league (no division configured, or
     * non-60 teams). Delegates to the existing flat {@link FixtureGenerator}.
     * Returns {@link DivisionFixtureRound} wrapping the flat output.
     */
    public List<DivisionFixtureRound> generateLegacyFixtures(List<TeamId> teamIds,
                                                              boolean isDoubleRound) {
        List<FixtureGenerator.FixtureRound> flat = flatScheduler.generate(teamIds, isDoubleRound);
        return flat.stream()
                .map(fr -> new DivisionFixtureRound(
                        fr.roundNumber(),
                        DivisionFixtureRound.Kind.INTRA, // legacy rounds are treated as intra
                        fr.matches().stream()
                                .map(slot -> new Matchup(slot.home(), slot.away()))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    private void validateAndGroup(List<Team> teams) {
        if (teams == null || teams.size() != TOTAL_TEAMS) {
            throw new IllegalArgumentException(
                    "Multi-division scheduler requires exactly " + TOTAL_TEAMS
                    + " teams (20 per division x 3), got " + (teams == null ? 0 : teams.size()));
        }
        Map<Division, Long> counts = teams.stream()
                .collect(Collectors.groupingBy(Team::getDivision, Collectors.counting()));
        for (Division d : Division.values()) {
            if (counts.getOrDefault(d, 0L) != TEAMS_PER_DIVISION) {
                throw new IllegalArgumentException(
                        "Division " + d + " has " + counts.getOrDefault(d, 0L)
                        + " teams, expected " + TEAMS_PER_DIVISION
                        + " (20 per division in 3-tier league)");
            }
        }
    }

    /**
     * Standard round-robin for N teams (N=20, even):
     * Returns N-1 ida legs + N-1 vuelta legs = 2*(N-1) legs total.
     * Each leg has N/2 matches. Total: 38 legs of 10 matches each = 380 matches.
     * Per team: 19 ida opponents + 19 vuelta opponents = 38 matches.
     *
     * <p>Algorithm: fix team[0], rotate the rest. After each leg, move
     * the last element to position 1. vuelta reverses home/away.
     */
    private List<List<Matchup>> intraDivisionRoundRobin(List<Team> teams) {
        int n = teams.size();
        int legsPerSide = n - 1;             // 19 for n=20
        int matchesPerRound = n / 2;         // 10 for n=20

        List<List<Matchup>> rounds = new ArrayList<>(legsPerSide * 2);

        // Ida: 19 rounds, each with 10 matches. Fix team[0], rotate rest.
        List<Team> rotation = new ArrayList<>(teams);
        for (int round = 0; round < legsPerSide; round++) {
            List<Matchup> roundMatches = new ArrayList<>(matchesPerRound);
            for (int i = 0; i < matchesPerRound; i++) {
                roundMatches.add(new Matchup(
                        rotation.get(i).getId(),
                        rotation.get(n - 1 - i).getId()));
            }
            rounds.add(roundMatches);
            Team last = rotation.remove(n - 1);
            rotation.add(1, last);
        }

        // Vuelta: same rotation pattern, but home/away reversed.
        rotation = new ArrayList<>(teams);
        for (int round = 0; round < legsPerSide; round++) {
            List<Matchup> roundMatches = new ArrayList<>(matchesPerRound);
            for (int i = 0; i < matchesPerRound; i++) {
                roundMatches.add(new Matchup(
                        rotation.get(n - 1 - i).getId(),
                        rotation.get(i).getId()));
            }
            rounds.add(roundMatches);
            Team last = rotation.remove(n - 1);
            rotation.add(1, last);
        }

        return rounds;
    }

    /**
     * Generate all unique cross-division matchups (P↔S 400 + P↔T 400 + S↔T 400 = 1200).
     */
    private List<Matchup> generateCrossMatchups(List<Team> primera,
                                                 List<Team> segunda,
                                                 List<Team> tercera) {
        List<Matchup> cross = new ArrayList<>(TEAMS_PER_DIVISION * TEAMS_PER_DIVISION * 3);
        // Interleave P-S and P-T for each P_i so greedy first-fit doesn't
        // dead-end (P-S first would fill all P teams before reaching P-T/S-T).
        // Order: P_0-S_0, P_0-T_0, P_0-S_1, P_0-T_1, ..., P_0-S_19, P_0-T_19,
        //        P_1-S_0, P_1-T_0, ..., P_19-T_19,
        //        S_0-T_0, ..., S_19-T_19.
        for (Team pt : primera) {
            for (int j = 0; j < TEAMS_PER_DIVISION; j++) {
                cross.add(new Matchup(pt.getId(), segunda.get(j).getId()));   // P_i-S_j
                cross.add(new Matchup(pt.getId(), tercera.get(j).getId()));   // P_i-T_j
            }
        }
        for (Team st : segunda) {
            for (Team tt : tercera) cross.add(new Matchup(st.getId(), tt.getId()));
        }
        return cross;
    }

    /**
     * Distribute matchups into matchdays using greedy first-fit.
     * Each matchday is a perfect matching (no team appears twice).
     * Returns at most {@code targetMatchdays} rounds, each with exactly {@code totalTeams / 2} matches.
     * If a perfect matching cannot be formed (e.g., not enough matchups), returns fewer rounds.
     */
    private List<List<Matchup>> distributeIntoMatchdaysConstructive(
            List<Team> primera, List<Team> segunda, List<Team> tercera) {
        final int N = TEAMS_PER_DIVISION; // 20
        final int HALF = N / 2;            // 10
        final int MATCHDAYS = CROSS_MATCHDAYS; // 40
        final int MATCHES_PER_DAY = TOTAL_TEAMS / 2; // 30

        List<Team> pA = primera.subList(0, HALF);
        List<Team> pB = primera.subList(HALF, N);
        List<Team> sA = segunda.subList(0, HALF);
        List<Team> sB = segunda.subList(HALF, N);
        List<Team> tA = tercera.subList(0, HALF);
        List<Team> tB = tercera.subList(HALF, N);

        // For each matchday m (0..39), determine which halves are active in each role.
        // m mod 4 determines the pattern:
        //   pattern 0: P_S=P_A, P_T=P_B, S_P=S_A, S_T=S_B, T_P=T_A, T_S=T_B
        //   pattern 1: P_S=P_A, P_T=P_B, S_P=S_B, S_T=S_A, T_P=T_B, T_S=T_A
        //   pattern 2: P_S=P_B, P_T=P_A, S_P=S_A, S_T=S_B, T_P=T_B, T_S=T_A
        //   pattern 3: P_S=P_B, P_T=P_A, S_P=S_B, S_T=S_A, T_P=T_A, T_S=T_B
        //
        // For each (P_S, S_P) combination (e.g., P_A × S_A), the pattern is
        // active for 10 matchdays. We use round-robin to assign pairs:
        //   matchday m (in those 10): P_i vs S_{(i + m_idx) mod 10}
        // where m_idx = (m - pattern) / 4 ∈ 0..9 is the index within the 10
        // matchdays for that pattern.

        List<List<Matchup>> matchdays = new ArrayList<>(MATCHDAYS);

        for (int m = 0; m < MATCHDAYS; m++) {
            int pattern = m % 4;
            List<Team> pS, pT, sP, sT, tP, tS;
            switch (pattern) {
                case 0:
                    pS = pA; pT = pB; sP = sA; sT = sB; tP = tA; tS = tB;
                    break;
                case 1:
                    pS = pA; pT = pB; sP = sB; sT = sA; tP = tB; tS = tA;
                    break;
                case 2:
                    pS = pB; pT = pA; sP = sA; sT = sB; tP = tB; tS = tA;
                    break;
                default: // 3
                    pS = pB; pT = pA; sP = sB; sT = sA; tP = tA; tS = tB;
                    break;
            }

            // Round-robin slot index: 0..9 across the 10 matchdays where
            // this pattern is active (m = pattern, pattern+4, pattern+8, ..., pattern+36).
            int mIdx = (m - pattern) / 4;

            List<Matchup> dayMatches = new ArrayList<>(MATCHES_PER_DAY);

            // P-S pairs: P_i (in pS) vs S_{(i + mIdx) mod HALF} (in sP)
            for (int i = 0; i < HALF; i++) {
                int sIdx = (i + mIdx) % HALF;
                dayMatches.add(new Matchup(pS.get(i).getId(), sP.get(sIdx).getId()));
            }
            // P-T pairs: P_i (in pT) vs T_{(i + mIdx) mod HALF} (in tP)
            for (int i = 0; i < HALF; i++) {
                int tIdx = (i + mIdx) % HALF;
                dayMatches.add(new Matchup(pT.get(i).getId(), tP.get(tIdx).getId()));
            }
            // S-T pairs: S_i (in sT) vs T_{(i + mIdx) mod HALF} (in tS)
            for (int i = 0; i < HALF; i++) {
                int tIdx = (i + mIdx) % HALF;
                dayMatches.add(new Matchup(sT.get(i).getId(), tS.get(tIdx).getId()));
            }

            matchdays.add(dayMatches);
        }

        return matchdays;
    }

    private String pairKey(Matchup m) {
        String a = m.home().getValue().toString();
        String b = m.away().getValue().toString();
        return a.compareTo(b) < 0 ? (a + "|" + b) : (b + "|" + a);
    }

    // ========== Value types ==========

    /**
     * A single round (matchday) in the season schedule. Contains 30 matches
     * for a 60-team league (or fewer for legacy leagues).
     */
    public record DivisionFixtureRound(int roundNumber, Kind kind, List<Matchup> matches) {
        public enum Kind { INTRA, CROSS }
        public DivisionFixtureRound {
            matches = List.copyOf(matches);
        }
        public int size() { return matches.size(); }
    }

    /**
     * A single match (home vs away).
     */
    public record Matchup(TeamId home, TeamId away) {}
}