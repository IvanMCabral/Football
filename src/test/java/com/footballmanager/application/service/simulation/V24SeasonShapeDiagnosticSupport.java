package com.footballmanager.application.service.simulation;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.valueobject.MatchFixture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V24D6K6: Support helpers for season-shaped diagnostic harness.
 *
 * <p>Provides utilities for:
 * <ul>
 *   <li>Multi-team league creation (8-20 teams)</li>
 *   <li>Round-robin fixture generation (home/away per round)</li>
 *   <li>25-player squad generation per team</li>
 *   <li>Season-shaped auto-select with injury/suspension/energy awareness</li>
 * </ul>
 *
 * <p>This is test-only infrastructure. No production behavior changes.
 */
public class V24SeasonShapeDiagnosticSupport {

    public static final int DEFAULT_SQUAD_SIZE = 25;

    /**
     * Creates a career save with multiple teams for season-shaped diagnostic.
     *
     * @param teamCount number of teams (8-20 supported)
     * @param squadSize players per team
     * @param rounds    fixture rounds to generate
     * @return initialized CareerSave with teams, players, and round-robin fixtures
     */
    public static SeasonShapeContext createSeasonShapeContext(int teamCount, int squadSize, int rounds) {
        if (teamCount < 2) throw new IllegalArgumentException("teamCount must be >= 2");
        if (squadSize < 11) throw new IllegalArgumentException("squadSize must be >= 11 for valid XI");
        if (rounds < 1) throw new IllegalArgumentException("rounds must be >= 1");

        CareerSave career = new CareerSave();
        career.getData().setCareerId("season_shape_" + System.currentTimeMillis());
        career.getSeasonManager().setCurrentSeason(1);

        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();
        Map<String, List<SessionPlayer>> teamPlayers = new java.util.HashMap<>();
        List<String> teamIds = new ArrayList<>();

        // Create teams and players
        for (int t = 0; t < teamCount; t++) {
            String teamId = UUID.randomUUID().toString();
            teamIds.add(teamId);

            SessionTeam team = SessionTeam.fromRealTeam(
                    UUID.fromString(teamId),
                    "world_" + teamId,
                    "Team " + (t + 1),
                    "Country",
                    BigDecimal.ZERO,
                    "4-3-3",
                    null);
            team.setSessionTeamId(teamId);
            tm.addSessionTeam(team);

            List<SessionPlayer> players = new ArrayList<>();
            for (int p = 0; p < squadSize; p++) {
                SessionPlayer player = SessionPlayer.custom(
                        "p_" + teamId + "_" + p,
                        25,
                        "MID",
                        75, 75, 75, 75, 75, 75,
                        BigDecimal.valueOf(1000));
                player.setEnergy(85);
                pm.addSessionPlayer(player);
                tm.assignPlayerToSquad(player.getSessionPlayerId(), teamId);
                players.add(player);
            }
            teamPlayers.put(teamId, players);

            // Set initial starting XI — first 11 players
            List<String> starterIds = players.stream()
                    .limit(11)
                    .map(SessionPlayer::getSessionPlayerId)
                    .collect(Collectors.toList());
            career.getTeamStarting11().put(teamId, new ArrayList<>(starterIds));
        }

        career.setTeamManager(tm);
        career.setPlayerManager(pm);

        // Generate round-robin fixtures
        List<MatchFixture> fixtures = generateRoundRobinFixtures(teamIds, rounds);
        career.setTournamentState(makeTournamentState(fixtures));

        return new SeasonShapeContext(career, teamIds, teamPlayers, fixtures);
    }

    /**
     * Generates round-robin fixtures ensuring each team plays once per round.
     * Each pair of teams plays each other twice (home and away) across the season.
     *
     * @param teamIds ordered list of team IDs
     * @param rounds  total rounds to generate
     * @return list of fixtures
     */
    public static List<MatchFixture> generateRoundRobinFixtures(List<String> teamIds, int rounds) {
        int n = teamIds.size();
        List<String> teams = new ArrayList<>(teamIds);

        List<MatchFixture> fixtures = new ArrayList<>();

        for (int round = 1; round <= rounds; round++) {
            // Build round teams list: team[0] stays fixed, others rotate
            // For odd n, each round has n/2 matches
            List<String> roundTeams = new ArrayList<>();
            roundTeams.add(teams.get(0));
            // rotating: teams 1 through n-1
            List<String> rotating = new ArrayList<>(teams.subList(1, n));
            // Rotate: last element moves to front
            if (!rotating.isEmpty() && round > 1) {
                String last = rotating.remove(rotating.size() - 1);
                rotating.add(0, last);
            }
            roundTeams.addAll(rotating);

            // Create n/2 matches per round
            for (int i = 0; i < n / 2; i++) {
                String home = roundTeams.get(i);
                String away = roundTeams.get(n - 1 - i);
                fixtures.add(new MatchFixture("s6_r" + round + "_m" + (i + 1), home, away, round));
            }
        }

        return fixtures;
    }

    /**
     * Selects starting XI for a team using availability and energy-based criteria.
     * Prefers higher energy players, excludes injured/suspended, avoids energy <= 20
     * when alternatives exist. Deterministic tiebreaker by playerId.
     */
    public static void selectStartingXI(CareerSave career, String teamId, int targetSize) {
        List<SessionPlayer> allPlayers = getTeamPlayers(career, teamId);

        List<SessionPlayer> available = new ArrayList<>();
        List<SessionPlayer> unavailable = new ArrayList<>();
        List<SessionPlayer> lowEnergy = new ArrayList<>();

        for (SessionPlayer p : allPlayers) {
            if (isPlayerAvailable(p)) {
                if (p.getEnergy() <= 20) lowEnergy.add(p);
                else available.add(p);
            } else {
                unavailable.add(p);
            }
        }

        // Sort available by energy desc, then playerId asc
        available.sort(Comparator
                .comparingInt((SessionPlayer p) -> p.getEnergy()).reversed()
                .thenComparing(SessionPlayer::getSessionPlayerId));

        // Sort low energy available by energy desc, then playerId asc
        lowEnergy.sort(Comparator
                .comparingInt((SessionPlayer p) -> p.getEnergy()).reversed()
                .thenComparing(SessionPlayer::getSessionPlayerId));

        // Sort unavailable by energy desc (for forced selection)
        unavailable.sort(Comparator
                .comparingInt((SessionPlayer p) -> p.getEnergy()).reversed()
                .thenComparing(SessionPlayer::getSessionPlayerId));

        List<SessionPlayer> selected = new ArrayList<>();

        // Add all energy > 20 available first
        for (SessionPlayer p : available) {
            if (selected.size() >= targetSize) break;
            selected.add(p);
        }

        // Fill remaining slots from low-energy available if needed
        if (selected.size() < targetSize) {
            for (SessionPlayer p : lowEnergy) {
                if (selected.size() >= targetSize) break;
                selected.add(p);
            }
        }

        // Final fallback: use unavailable if still not enough
        if (selected.size() < targetSize) {
            for (SessionPlayer p : unavailable) {
                if (selected.size() >= targetSize) break;
                selected.add(p);
            }
        }

        List<String> starterIds = selected.stream()
                .map(SessionPlayer::getSessionPlayerId)
                .collect(Collectors.toList());

        career.getTeamStarting11().put(teamId, new ArrayList<>(starterIds));
    }

    /**
     * Attempts energy-based rotation: swaps lowest-energy starter with best available bench player
     * if bench has significantly more energy.
     *
     * @return number of rotations performed (0 or 1)
     */
    public static int rotateDueToEnergy(CareerSave career, String teamId, int energyThreshold, int energyAdvantage) {
        List<String> starterIds = career.getTeamStarting11().get(teamId);
        if (starterIds == null || starterIds.isEmpty()) return 0;

        List<SessionPlayer> currentXI = starterIds.stream()
                .map(id -> career.getSessionPlayer(id))
                .filter(p -> p != null)
                .collect(Collectors.toList());

        if (currentXI.isEmpty()) return 0;

        int minStarterEnergy = currentXI.stream()
                .mapToInt(SessionPlayer::getEnergy).min().orElse(100);

        if (minStarterEnergy >= energyThreshold) return 0;

        // Find best available bench player for this team
        List<SessionPlayer> benchPlayers = getTeamPlayers(career, teamId).stream()
                .filter(p -> !starterIds.contains(p.getSessionPlayerId()))
                .filter(V24SeasonShapeDiagnosticSupport::isPlayerAvailable)
                .collect(Collectors.toList());

        if (benchPlayers.isEmpty()) return 0;

        int bestBenchEnergy = benchPlayers.stream()
                .mapToInt(SessionPlayer::getEnergy).max().orElse(0);

        if (bestBenchEnergy <= minStarterEnergy + energyAdvantage) return 0;

        SessionPlayer worstStarter = currentXI.stream()
                .min(Comparator.comparingInt(SessionPlayer::getEnergy))
                .orElse(null);

        SessionPlayer bestBench = benchPlayers.stream()
                .max(Comparator.comparingInt(SessionPlayer::getEnergy))
                .orElse(null);

        if (worstStarter == null || bestBench == null) return 0;

        // Perform swap
        List<String> newStarters = new ArrayList<>(starterIds);
        int idx = newStarters.indexOf(worstStarter.getSessionPlayerId());
        if (idx >= 0) {
            newStarters.set(idx, bestBench.getSessionPlayerId());
            career.getTeamStarting11().put(teamId, newStarters);
            return 1;
        }

        return 0;
    }

    /**
     * Checks if a player is available (not injured, not suspended).
     * Mirrors the production isPlayerAvailable logic used in J3 lineup selection.
     */
    public static boolean isPlayerAvailable(SessionPlayer player) {
        if (Boolean.TRUE.equals(player.getInjured())) return false;
        if (player.getInjuryRemainingMatches() != null && player.getInjuryRemainingMatches() > 0) return false;
        if (Boolean.TRUE.equals(player.getSuspended())) return false;
        if (player.getSuspensionRemainingMatches() != null && player.getSuspensionRemainingMatches() > 0) return false;
        return true;
    }

    /**
     * Returns count of available players for a team.
     */
    public static int countAvailablePlayers(CareerSave career, String teamId) {
        return (int) getTeamPlayers(career, teamId).stream()
                .filter(V24SeasonShapeDiagnosticSupport::isPlayerAvailable)
                .count();
    }

    /**
     * Returns all players for a given team.
     */
    public static List<SessionPlayer> getTeamPlayers(CareerSave career, String teamId) {
        List<String> playerIds = career.getTeamManager().getSquadPlayerIds(teamId);
        List<SessionPlayer> players = new ArrayList<>();
        for (String id : playerIds) {
            SessionPlayer p = career.getSessionPlayer(id);
            if (p != null) players.add(p);
        }
        return players;
    }

    /**
     * Counts unique unavailable players (injured OR suspended) for a team.
     */
    public static int countUniqueUnavailable(CareerSave career, String teamId) {
        Set<String> unavailable = new HashSet<>();
        for (SessionPlayer p : getTeamPlayers(career, teamId)) {
            if (!isPlayerAvailable(p)) {
                unavailable.add(p.getSessionPlayerId());
            }
        }
        return unavailable.size();
    }

    /**
     * Gets unique unavailable player IDs for a team.
     */
    public static Set<String> getUniqueUnavailableIds(CareerSave career, String teamId) {
        Set<String> unavailable = new HashSet<>();
        for (SessionPlayer p : getTeamPlayers(career, teamId)) {
            if (!isPlayerAvailable(p)) {
                unavailable.add(p.getSessionPlayerId());
            }
        }
        return unavailable;
    }

    /**
     * Gets currently injured player IDs for a team.
     */
    public static Set<String> getInjuredIds(CareerSave career, String teamId) {
        Set<String> ids = new HashSet<>();
        for (SessionPlayer p : getTeamPlayers(career, teamId)) {
            if (Boolean.TRUE.equals(p.getInjured()) ||
                    (p.getInjuryRemainingMatches() != null && p.getInjuryRemainingMatches() > 0)) {
                ids.add(p.getSessionPlayerId());
            }
        }
        return ids;
    }

    /**
     * Gets currently suspended player IDs for a team.
     */
    public static Set<String> getSuspendedIds(CareerSave career, String teamId) {
        Set<String> ids = new HashSet<>();
        for (SessionPlayer p : getTeamPlayers(career, teamId)) {
            if (Boolean.TRUE.equals(p.getSuspended()) ||
                    (p.getSuspensionRemainingMatches() != null && p.getSuspensionRemainingMatches() > 0)) {
                ids.add(p.getSessionPlayerId());
            }
        }
        return ids;
    }

    /**
     * Returns average squad energy for a team (all players).
     */
    public static double computeSquadAvgEnergy(CareerSave career, String teamId) {
        List<SessionPlayer> players = getTeamPlayers(career, teamId);
        if (players.isEmpty()) return 0;
        int sum = 0;
        for (SessionPlayer p : players) sum += p.getEnergy();
        return (double) sum / players.size();
    }

    /**
     * Counts players below energy threshold for a team.
     */
    public static int countPlayersBelowEnergy(CareerSave career, String teamId, int threshold) {
        int count = 0;
        for (SessionPlayer p : getTeamPlayers(career, teamId)) {
            if (p.getEnergy() < threshold) count++;
        }
        return count;
    }

    /**
     * Counts players above energy threshold for a team.
     */
    public static int countPlayersAboveEnergy(CareerSave career, String teamId, int threshold) {
        int count = 0;
        for (SessionPlayer p : getTeamPlayers(career, teamId)) {
            if (p.getEnergy() > threshold) count++;
        }
        return count;
    }

    /**
     * Returns average form for a team.
     */
    public static double computeTeamAvgForm(CareerSave career, String teamId) {
        List<SessionPlayer> players = getTeamPlayers(career, teamId);
        if (players.isEmpty()) return 50;
        int sum = 0;
        for (SessionPlayer p : players) sum += (p.getForm() != null ? p.getForm() : 50);
        return (double) sum / players.size();
    }

    /**
     * Returns min form for a team.
     */
    public static int computeTeamMinForm(CareerSave career, String teamId) {
        return getTeamPlayers(career, teamId).stream()
                .mapToInt(p -> p.getForm() != null ? p.getForm() : 50)
                .min().orElse(50);
    }

    /**
     * Returns max form for a team.
     */
    public static int computeTeamMaxForm(CareerSave career, String teamId) {
        return getTeamPlayers(career, teamId).stream()
                .mapToInt(p -> p.getForm() != null ? p.getForm() : 50)
                .max().orElse(50);
    }

    /**
     * Returns total yellow cards for a team.
     */
    public static int countTeamYellowCards(CareerSave career, String teamId) {
        int total = 0;
        for (SessionPlayer p : getTeamPlayers(career, teamId)) {
            if (p.getYellowCards() != null) total += p.getYellowCards();
        }
        return total;
    }

    /**
     * Returns total red cards for a team.
     */
    public static int countTeamRedCards(CareerSave career, String teamId) {
        int total = 0;
        for (SessionPlayer p : getTeamPlayers(career, teamId)) {
            if (p.getRedCards() != null && p.getRedCards() > 0) total++;
        }
        return total;
    }

    private static TournamentState makeTournamentState(List<MatchFixture> fixtures) {
        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        for (MatchFixture f : fixtures) ts.getFixtures().add(f);
        return ts;
    }

    // ========================================================================
    // Data Classes
    // ========================================================================

    public static class SeasonShapeContext {
        private final CareerSave career;
        private final List<String> teamIds;
        private final Map<String, List<SessionPlayer>> teamPlayers;
        private final List<MatchFixture> fixtures;

        public SeasonShapeContext(CareerSave career, List<String> teamIds,
                                  Map<String, List<SessionPlayer>> teamPlayers,
                                  List<MatchFixture> fixtures) {
            this.career = career;
            this.teamIds = teamIds;
            this.teamPlayers = teamPlayers;
            this.fixtures = fixtures;
        }

        public CareerSave career() { return career; }
        public List<String> teamIds() { return teamIds; }
        public Map<String, List<SessionPlayer>> teamPlayers() { return teamPlayers; }
        public List<MatchFixture> fixtures() { return fixtures; }
    }
}