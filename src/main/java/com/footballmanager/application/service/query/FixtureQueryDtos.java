package com.footballmanager.application.service.query;

import java.util.List;
import java.util.Map;

/**
 * DTOs para consultas de fixtures.
 */
public final class FixtureQueryDtos {

    private FixtureQueryDtos() {}

    public record MatchInfo(
            String matchId,
            String homeTeamId,
            String homeTeamName,
            String awayTeamId,
            String awayTeamName,
            Integer round,
            String status,
            Integer homeGoals,
            Integer awayGoals,
            Double homeXG,
            Double awayXG,
            Double totalXG
    ) {}

    public record RoundInfo(
            Integer round,
            String phase,
            String phaseLabel,
            List<MatchInfo> matches,
            String byeTeam,
            Integer totalMatches
    ) {}

    public record DivisionConfig(
            Integer numTeams,
            Integer matchesPerRound,
            Boolean hasBye,
            Integer idaRounds,
            Integer vueltaRounds
    ) {}

    public record TeamInfo(
            String id,
            String name
    ) {}

    public record AllFixturesResponse(
            String careerId,
            Integer totalRounds,
            List<RoundInfo> rounds,
            List<TeamInfo> teams,
            Map<String, String> teamNames,
            DivisionConfig config
    ) {}

    public record DivisionFixtures(
            String divisionId,
            String divisionName,
            Boolean isUserDivision,
            List<RoundInfo> rounds
    ) {}

    public record CompleteFixturesResponse(
            String careerId,
            Integer currentRound,
            Integer totalRounds,
            List<DivisionFixtures> divisions,
            List<TeamInfo> freeTeams
    ) {}

    public record LeagueMatchInfo(
            String matchId,
            String homeTeamId,
            String homeTeamName,
            String awayTeamId,
            String awayTeamName,
            Integer round,
            String status,
            Integer homeGoals,
            Integer awayGoals,
            Double homeXG,
            Double awayXG,
            Double totalXG
    ) {}

    public record LeagueDivisionFixtures(
            String divisionId,
            String divisionName,
            Boolean isUserDivision,
            List<LeagueMatchInfo> fixtures,
            Boolean hasBye
    ) {}

    // UX-6: BYE indicator DTOs
    public record RoundFixturesWithBye(
            Integer round,
            List<MatchInfo> matches,
            String byeTeam
    ) {}

    public record AllRoundsWithBye(List<RoundFixturesWithBye> rounds) {}
}
