package com.footballmanager.application.service.season;

import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.TeamId;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * V25D78-C55.2 phase 3: Promotion/Relegation logic for multi-division leagues.
 *
 * <p>At end of season:
 * <ul>
 *   <li>Top 3 of SEGUNDA ascend to PRIMERA</li>
 *   <li>Bottom 3 of PRIMERA descend to SEGUNDA</li>
 *   <li>Top 3 of TERCERA ascend to SEGUNDA</li>
 *   <li>Bottom 3 of SEGUNDA descend to TERCERA</li>
 * </ul>
 *
 * <p>Standings ordering (per spec C55.2):
 * <ol>
 *   <li>Points (3 for win, 1 for draw)</li>
 *   <li>Goal difference (tiebreaker)</li>
 *   <li>Goals scored (tiebreaker)</li>
 *   <li>Head-to-head (tiebreaker — currently stubbed as random for ties beyond goals scored)</li>
 * </ol>
 *
 * <p>Input: a list of {@link Standing} per team (with points, GD, GS).
 * Output: a map of {@code TeamId -> newDivision} describing which teams
 * should change tier for the next season.
 *
 * <p>The service does NOT modify the database directly — it returns the
 * movement plan. Phase 5 (integration tests) or a downstream command
 * (not part of C55.2) will apply these changes.
 */
@Service
public class PromotionRelegationService {

    public static final int TEAMS_PROMOTED_OR_RELEGATED = 3;

    /**
     * Calculate end-of-season movements for a 60-team multi-division league.
     *
     * @param standingsByDivision map of division → list of standings for
     *                            teams in that division. Each list MUST
     *                            contain exactly 20 standings (one per team).
     * @return movement map: TeamId → newDivision. Teams not in the map
     *         stay in their current division.
     * @throws IllegalArgumentException if any division doesn't have 20 teams.
     */
    public Map<TeamId, Division> calculateMovements(
            Map<Division, List<Standing>> standingsByDivision) {
        validateInput(standingsByDivision);

        Map<TeamId, Division> movements = new HashMap<>();

        // Sort each division's standings by points/GD/GS.
        Map<Division, List<Standing>> sorted = new EnumMap<>(Division.class);
        for (Map.Entry<Division, List<Standing>> entry : standingsByDivision.entrySet()) {
            sorted.put(entry.getKey(),
                    entry.getValue().stream()
                            .sorted(Comparator
                                    // Primary: points (descending)
                                    .comparingInt(Standing::points).reversed()
                                    // Secondary: goal difference (descending)
                                    .thenComparing(Comparator.comparingInt(Standing::goalDifference).reversed())
                                    // Tertiary: goals scored (descending)
                                    .thenComparing(Comparator.comparingInt(Standing::goalsScored).reversed())
                                    // Final: head-to-head (stub: by teamId for determinism)
                                    .thenComparing(s -> s.teamId().getValue().toString()))
                            .collect(Collectors.toList()));
        }

        // Top 3 of SEGUNDA → PRIMERA.
        applyPromotion(sorted.get(Division.SEGUNDA), Division.PRIMERA, movements);
        // Bottom 3 of PRIMERA → SEGUNDA.
        applyRelegation(sorted.get(Division.PRIMERA), Division.SEGUNDA, movements);
        // Top 3 of TERCERA → SEGUNDA.
        applyPromotion(sorted.get(Division.TERCERA), Division.SEGUNDA, movements);
        // Bottom 3 of SEGUNDA → TERCERA.
        applyRelegation(sorted.get(Division.SEGUNDA), Division.TERCERA, movements);

        return movements;
    }

    private void applyPromotion(List<Standing> division, Division target,
                                 Map<TeamId, Division> movements) {
        // Top 3 (sorted descending) get promoted.
        for (int i = 0; i < TEAMS_PROMOTED_OR_RELEGATED && i < division.size(); i++) {
            movements.put(division.get(i).teamId(), target);
        }
    }

    private void applyRelegation(List<Standing> division, Division target,
                                   Map<TeamId, Division> movements) {
        // Bottom 3 (already sorted descending, so the LAST 3) get relegated.
        int size = division.size();
        for (int i = 0; i < TEAMS_PROMOTED_OR_RELEGATED && i < size; i++) {
            int idx = size - 1 - i;
            movements.put(division.get(idx).teamId(), target);
        }
    }

    /**
     * Apply the movement plan to a list of teams, returning a new list with
     * updated divisions. Teams in the movement map get their division changed.
     */
    public List<Team> applyMovements(List<Team> teams, Map<TeamId, Division> movements) {
        List<Team> updated = new ArrayList<>(teams.size());
        for (Team team : teams) {
            Division newDiv = movements.get(team.getId());
            if (newDiv == null) {
                updated.add(team);
            } else {
                // Reconstruct with new division (immutable Team).
                updated.add(Team.create(team.getId(), team.getManagerId(),
                        team.getName(), team.getCountry(),
                        team.getBudget(), team.getFormation(), newDiv));
            }
        }
        return updated;
    }

    private void validateInput(Map<Division, List<Standing>> standingsByDivision) {
        if (standingsByDivision == null || standingsByDivision.size() != Division.values().length) {
            throw new IllegalArgumentException(
                    "standingsByDivision must contain all 3 divisions");
        }
        for (Division d : Division.values()) {
            List<Standing> list = standingsByDivision.get(d);
            if (list == null || list.size() != 20) {
                throw new IllegalArgumentException(
                        "Division " + d + " has " + (list == null ? 0 : list.size())
                        + " standings, expected 20");
            }
        }
    }

    /**
     * End-of-season standing for a single team.
     *
     * @param teamId        team identifier
     * @param points        total points (3 per win, 1 per draw)
     * @param goalsScored   goals scored across all matches
     * @param goalsConceded goals conceded across all matches
     */
    public record Standing(TeamId teamId, int points, int goalsScored, int goalsConceded) {
        public int goalDifference() {
            return goalsScored - goalsConceded;
        }
    }
}