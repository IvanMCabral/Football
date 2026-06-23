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
                null, null, null,
                deriveRoundId(careerId, f.getRound())
        );
    }

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
                deriveRoundId(career.getCareerId(), f.getRound())
        );
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
