package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;

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

    public static MatchInfo toMatchInfo(MatchFixture f, Map<String, String> teamNames) {
        return new MatchInfo(
                f.getMatchId(),
                f.getHomeTeamId(),
                getTeamName(teamNames, f.getHomeTeamId()),
                f.getAwayTeamId(),
                getTeamName(teamNames, f.getAwayTeamId()),
                f.getRound(),
                f.getStatus() != null ? f.getStatus().name() : "PENDING",
                f.getResult() != null ? f.getResult().getHomeGoals() : null,
                f.getResult() != null ? f.getResult().getAwayGoals() : null
        );
    }

    public static LeagueMatchInfo toLeagueMatchInfo(MatchFixture f, Map<String, String> teamNames) {
        return new LeagueMatchInfo(
                f.getMatchId().toString(),
                f.getHomeTeamId(),
                getTeamName(teamNames, f.getHomeTeamId()),
                f.getAwayTeamId(),
                getTeamName(teamNames, f.getAwayTeamId()),
                f.getRound(),
                f.getStatus().name(),
                f.getResult() != null ? f.getResult().getHomeGoals() : null,
                f.getResult() != null ? f.getResult().getAwayGoals() : null
        );
    }

    public static List<MatchInfo> toMatchInfoList(List<MatchFixture> fixtures, Map<String, String> teamNames) {
        return fixtures.stream().map(f -> toMatchInfo(f, teamNames)).toList();
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
            Map<String, String> teamNames, List<String> teamIds, int totalRounds, int idaRounds) {
        List<RoundInfo> rounds = new ArrayList<>();
        for (int r = 1; r <= totalRounds; r++) {
            final int currentRound = r;
            boolean isIda = currentRound <= idaRounds;
            List<MatchFixture> roundFixtures = allFixtures.stream().filter(f -> f.getRound() == currentRound).toList();
            String byeTeamName = findByeTeam(roundFixtures, teamIds, teamNames);
            List<MatchInfo> matches = toMatchInfoList(roundFixtures, teamNames);
            rounds.add(new RoundInfo(r, isIda ? "IDA" : "VUELTA", isIda ? "Primera Vuelta" : "Segunda Vuelta", matches, byeTeamName, matches.size()));
        }
        return rounds;
    }

    public static List<RoundInfo> buildRoundInfosSimple(List<MatchFixture> allFixtures,
            Map<String, String> teamNames, Set<String> divisionTeamIds, int totalRounds) {
        List<RoundInfo> rounds = new ArrayList<>();
        for (int r = 1; r <= totalRounds; r++) {
            final int currentRound = r;
            List<MatchFixture> roundFixtures = allFixtures.stream()
                    .filter(f -> f.getRound() == currentRound)
                    .filter(f -> divisionTeamIds.contains(f.getHomeTeamId()) && divisionTeamIds.contains(f.getAwayTeamId()))
                    .toList();
            String byeTeamName = findByeTeam(roundFixtures, new ArrayList<>(divisionTeamIds), teamNames);
            List<MatchInfo> matches = toMatchInfoList(roundFixtures, teamNames);
            rounds.add(new RoundInfo(currentRound, null, null, matches, byeTeamName, matches.size()));
        }
        return rounds;
    }
}
