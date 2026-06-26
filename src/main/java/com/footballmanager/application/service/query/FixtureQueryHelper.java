package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.footballmanager.application.service.query.FixtureQueryDtos.*;

/**
 * Helper para construcción de fixtures.
 * Lógica compartida entre servicios de consulta de fixtures.
 */
public final class FixtureQueryHelper {

    private FixtureQueryHelper() {}

    public static Map<String, String> buildTeamNamesMap(CareerSave career, Collection<String> teamIds) {
        Map<String, String> teamNames = new HashMap<>();
        for (String teamId : teamIds) {
            SessionTeam team = career.getSessionTeam(teamId);
            if (team != null) teamNames.put(team.getSessionTeamId(), team.getName());
        }
        return teamNames;
    }

    /**
     * V24D24.3-FIX: Extract the distinct set of team IDs that appear as home or away
     * across the given fixtures. Used to build a complete teamNames map that covers
     * cross-division fixtures injected via test-harness {@code replaceFixtures}.
     *
     * <p>Previously, callers passed {@code userDivision.getTeamIds()} which missed
     * any team from another division present in the round's fixtures — the fallback
     * {@link #getTeamName(Map, String)} returned the UUID, leaking raw IDs to the UI
     * (BUG_FIXTURES_TEAM_NAMES_UUID_V2).
     */
    public static Set<String> extractTeamIdsFromFixtures(Collection<MatchFixture> fixtures) {
        if (fixtures == null || fixtures.isEmpty()) return Set.of();
        Set<String> teamIds = new HashSet<>();
        for (MatchFixture f : fixtures) {
            if (f.getHomeTeamId() != null) teamIds.add(f.getHomeTeamId());
            if (f.getAwayTeamId() != null) teamIds.add(f.getAwayTeamId());
        }
        return teamIds;
    }

    public static String getTeamName(Map<String, String> teamNames, String teamId) {
        return teamNames.getOrDefault(teamId, teamId);
    }

    /**
     * V24D24.2: Deriva un roundId determinístico (UUID v3 sobre nameUUIDFromBytes)
     * a partir de (careerId, round). Esto permite que el front llame a
     * {@code POST /api/v1/match-engine/rounds/start} con el roundId que el back
     * ya conoce, sin necesidad de registrarlo antes.
     *
     * <p>Para matches dentro de un round ya registrado como engine vivo, se
     * prefiere el roundId real del registry; este método es el fallback
     * determinístico para rounds futuros / pasados.
     */
    public static String deriveRoundId(String careerId, int round) {
        if (careerId == null) return null;
        String key = "roundId|" + careerId + "|" + round;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * V25D37-F1: Build a {@link MatchInfo} when only the {@code careerId} (string)
     * is available — no {@link CareerSave} to derive formations / xG from.
     *
     * <p>Previous implementation delegated to {@code toMatchInfo(f, teamNames, (String) null)}
     * which resolved to this same overload (the {@code (String) null} cast disambiguated
     * from {@code CareerSave} but the resolved signature was still the {@code String careerId}
     * overload), producing infinite recursion and a {@link StackOverflowError} at runtime
     * (BUG_STACKOVERFLOW_V25D24_3_F1, surfaced via
     * {@code UserDivisionFixtureQueryServiceTest.getAll_withCrossDivisionFixture_returnsRealNames}).
     *
     * <p>Fix: build the {@link MatchInfo} directly. Formation + xG fields stay {@code null}
     * because this overload has no access to a {@link CareerSave} — callers that need
     * formations / xG must use the {@link #toMatchInfo(MatchFixture, Map, CareerSave)}
     * overload instead.
     */
    public static MatchInfo toMatchInfo(MatchFixture f, Map<String, String> teamNames, String careerId) {
        return new MatchInfo(
                f.getMatchId(),
                f.getHomeTeamId(),
                getTeamName(teamNames, f.getHomeTeamId()),
                f.getAwayTeamId(),
                getTeamName(teamNames, f.getAwayTeamId()),
                f.getRound(),
                f.getStatus() != null ? f.getStatus().name() : "PENDING",
                f.getResult() != null ? f.getResult().getHomeGoals() : null,
                f.getResult() != null ? f.getResult().getAwayGoals() : null,
                null,   // homeXG — requires CareerSave
                null,   // awayXG — requires CareerSave
                null,   // totalXG — requires CareerSave
                deriveRoundId(careerId, f.getRound()),
                null,   // homeFormation — requires CareerSave
                null    // awayFormation — requires CareerSave
        );
    }

    /**
     * V24D24.6: MatchInfo overload that hydrates {@code homeFormation} /
     * {@code awayFormation} from {@code career.getTeamStarting11Formation()}
     * (the V24 engine's source of truth for formations — see
     * {@code TestHarnessUseCaseImpl.executeSetFormation}). Also computes
     * xG metrics (which the {@code String careerId} overload cannot
     * because it has no access to the career). Nullable formations: a
     * team may not have a formation recorded yet (e.g. brand-new career
     * before any {@code set-formation} call, or a BYE team).
     */
    public static MatchInfo toMatchInfo(MatchFixture f, Map<String, String> teamNames, CareerSave career) {
        if (career == null) {
            return toMatchInfo(f, teamNames, (String) null);
        }
        com.footballmanager.domain.model.entity.SessionTeam homeTeam = career.getSessionTeam(f.getHomeTeamId());
        com.footballmanager.domain.model.entity.SessionTeam awayTeam = career.getSessionTeam(f.getAwayTeamId());
        com.footballmanager.domain.model.valueobject.MatchQualityMetrics metrics = null;
        if (homeTeam != null && awayTeam != null) {
            int homeOvr = calculateSessionTeamOvr(career, f.getHomeTeamId());
            int awayOvr = calculateSessionTeamOvr(career, f.getAwayTeamId());
            var lambdas = com.footballmanager.application.service.domain.MatchQualityComputer.computeLambdas(homeOvr, awayOvr);
            metrics = com.footballmanager.domain.model.valueobject.MatchQualityMetrics.fromLambdas(lambdas);
        }
        String homeFormation = resolveFormation(career, f.getHomeTeamId());
        String awayFormation = resolveFormation(career, f.getAwayTeamId());
        return new MatchInfo(
                f.getMatchId(),
                f.getHomeTeamId(),
                getTeamName(teamNames, f.getHomeTeamId()),
                f.getAwayTeamId(),
                getTeamName(teamNames, f.getAwayTeamId()),
                f.getRound(),
                f.getStatus() != null ? f.getStatus().name() : "PENDING",
                f.getResult() != null ? f.getResult().getHomeGoals() : null,
                f.getResult() != null ? f.getResult().getAwayGoals() : null,
                metrics != null ? metrics.homeXg() : null,
                metrics != null ? metrics.awayXg() : null,
                metrics != null ? metrics.totalXg() : null,
                deriveRoundId(career.getCareerId(), f.getRound()),
                homeFormation,
                awayFormation
        );
    }

    /**
     * V24D24.6: read a team's formation from the V24 engine's source of
     * truth ({@code career.getTeamStarting11Formation()}). Falls back to
     * {@code sessionTeam.getFormation()} if the map is empty (e.g. legacy
     * career created before the map was introduced in V24D24). Returns
     * null if neither source has a value — the UI renders "—" for null.
     */
    private static String resolveFormation(CareerSave career, String teamId) {
        if (teamId == null) return null;
        Map<String, String> formations = career.getTeamStarting11Formation();
        String fromMap = (formations != null) ? formations.get(teamId) : null;
        if (fromMap != null && !fromMap.isBlank()) {
            return fromMap;
        }
        com.footballmanager.domain.model.entity.SessionTeam team = career.getSessionTeam(teamId);
        if (team != null) {
            String fromTeam = team.getFormation();
            if (fromTeam != null && !fromTeam.isBlank()) {
                return fromTeam;
            }
        }
        return null;
    }

    private static int calculateSessionTeamOvr(CareerSave career, String teamId) {
        java.util.List<String> playerIds = career.getSquadPlayerIds(teamId);
        if (playerIds == null || playerIds.isEmpty()) {
            return 70;
        }
        int totalOvr = 0;
        int count = 0;
        for (String playerId : playerIds) {
            var player = career.getSessionPlayer(playerId);
            if (player != null) {
                totalOvr += player.calculateOverall();
                count++;
            }
        }
        return count > 0 ? totalOvr / count : 70;
    }

    public static LeagueMatchInfo toLeagueMatchInfo(MatchFixture f, Map<String, String> teamNames, String careerId) {
        return new LeagueMatchInfo(
                f.getMatchId().toString(),
                f.getHomeTeamId(),
                getTeamName(teamNames, f.getHomeTeamId()),
                f.getAwayTeamId(),
                getTeamName(teamNames, f.getAwayTeamId()),
                f.getRound(),
                f.getStatus().name(),
                f.getResult() != null ? f.getResult().getHomeGoals() : null,
                f.getResult() != null ? f.getResult().getAwayGoals() : null,
                null, null, null,
                deriveRoundId(careerId, f.getRound())
        );
    }

    public static List<MatchInfo> toMatchInfoList(List<MatchFixture> fixtures, Map<String, String> teamNames, String careerId) {
        return fixtures.stream().map(f -> toMatchInfo(f, teamNames, careerId)).toList();
    }

    public static String findByeTeam(List<MatchFixture> roundFixtures, List<String> teamIds, Map<String, String> teamNames) {
        Set<String> teamsWithMatch = new HashSet<>();
        roundFixtures.forEach(f -> {
            teamsWithMatch.add(f.getHomeTeamId());
            if (f.getAwayTeamId() != null) teamsWithMatch.add(f.getAwayTeamId());
        });
        for (String teamId : teamIds) if (!teamsWithMatch.contains(teamId)) return getTeamName(teamNames, teamId);
        return null;
    }

    public static List<RoundInfo> buildRoundInfosWithPhase(List<MatchFixture> allFixtures,
            Map<String, String> teamNames, List<String> teamIds, int totalRounds, int idaRounds, String careerId) {
        List<RoundInfo> rounds = new ArrayList<>();
        for (int r = 1; r <= totalRounds; r++) {
            final int currentRound = r;
            boolean isIda = currentRound <= idaRounds;
            List<MatchFixture> roundFixtures = allFixtures.stream().filter(f -> f.getRound() == currentRound).toList();
            String byeTeamName = findByeTeam(roundFixtures, teamIds, teamNames);
            List<MatchInfo> matches = toMatchInfoList(roundFixtures, teamNames, careerId);
            rounds.add(new RoundInfo(r, isIda ? "IDA" : "VUELTA", isIda ? "Primera Vuelta" : "Segunda Vuelta", matches, byeTeamName, matches.size()));
        }
        return rounds;
    }

    public static List<RoundInfo> buildRoundInfosSimple(List<MatchFixture> allFixtures,
            Map<String, String> teamNames, Set<String> divisionTeamIds, int totalRounds, String careerId) {
        List<RoundInfo> rounds = new ArrayList<>();
        for (int r = 1; r <= totalRounds; r++) {
            final int currentRound = r;
            List<MatchFixture> roundFixtures = allFixtures.stream()
                    .filter(f -> f.getRound() == currentRound)
                    .filter(f -> divisionTeamIds.contains(f.getHomeTeamId()) && divisionTeamIds.contains(f.getAwayTeamId()))
                    .toList();
            String byeTeamName = findByeTeam(roundFixtures, new ArrayList<>(divisionTeamIds), teamNames);
            List<MatchInfo> matches = toMatchInfoList(roundFixtures, teamNames, careerId);
            rounds.add(new RoundInfo(currentRound, null, null, matches, byeTeamName, matches.size()));
        }
        return rounds;
    }
}
