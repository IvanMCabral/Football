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
            TeamStrengthInfo homeStrength,
            String awayTeamId,
            String awayTeamName,
            TeamStrengthInfo awayStrength,
            Integer round,
            String status,
            Integer homeGoals,
            Integer awayGoals,
            Double homeXG,
            Double awayXG,
            Double totalXG,
            String roundId,
            // career.teamStarting11Formation which is the detailed match engine's
            // source of truth). Nullable: a team may not have a formation
            // recorded yet (e.g. brand-new career, BYE team).
            String homeFormation,
            String awayFormation
    ) {}

    public record TeamStrengthInfo(
            Integer squadOvr,
            Integer startingOvr,
            Integer avgEnergy,
            Integer avgForm,
            Integer avgStamina,
            Integer squadSize,
            Integer starterCount
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
            Double totalXG,
            String roundId
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

    public record AllRoundsWithBye(
            List<RoundFixturesWithBye> rounds,
            // career.getUserSessionTeamId(). The frontend uses this to
            // highlight the user-controllable team in the test-harness
            // Panel B (formations snapshot). Nullable: a fresh career
            // may not have a user team assigned yet.
            String userSessionTeamId
    ) {}
}
