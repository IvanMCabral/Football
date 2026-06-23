package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.MatchQualityMetrics;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

import static com.footballmanager.application.service.query.FixtureQueryDtos.*;

/**
 * Servicio de consultas para fixtures de la division del usuario.
 */
@Service
public class UserDivisionFixtureQueryService {

    public Mono<List<MatchInfo>> getByRound(CareerSave career, int round) {
        return Mono.fromCallable(() -> {
            Division userDivision = career.getUserDivision();
            if (userDivision == null) {
                return Collections.<MatchInfo>emptyList();
            }

            TournamentState tournamentState = career.getTournamentState();
            List<MatchFixture> fixtures = tournamentState.getFixturesForRound(round);
            Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, userDivision.getTeamIds());

            return fixtures.stream().map(f -> {
                int homeOvr = calculateSessionTeamOvr(career, f.getHomeTeamId());
                int awayOvr = calculateSessionTeamOvr(career, f.getAwayTeamId());
                var lambdas = com.footballmanager.application.service.domain.MatchQualityComputer.computeLambdas(homeOvr, awayOvr);
                var metrics = com.footballmanager.domain.model.valueobject.MatchQualityMetrics.fromLambdas(lambdas);
                return new MatchInfo(
                        f.getMatchId(),
                        f.getHomeTeamId(),
                        teamNames.get(f.getHomeTeamId()),
                        f.getAwayTeamId(),
                        teamNames.get(f.getAwayTeamId()),
                        f.getRound(),
                        f.getStatus() != null ? f.getStatus().name() : "PENDING",
                        f.getResult() != null ? f.getResult().getHomeGoals() : null,
                        f.getResult() != null ? f.getResult().getAwayGoals() : null,
                        metrics.homeXg(),
                        metrics.awayXg(),
                        metrics.totalXg(),
                        FixtureQueryHelper.deriveRoundId(career.getCareerId(), f.getRound())
                );
            }).toList();
        });
    }

    private int calculateSessionTeamOvr(CareerSave career, String teamId) {
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

    public Mono<AllFixturesResponse> getAll(CareerSave career) {
        return Mono.fromCallable(() -> {
            Division userDivision = career.getUserDivision();
            if (userDivision == null) {
                return createEmptyResponse();
            }

            TournamentState tournamentState = career.getTournamentState();
            int numTeams = userDivision.getTeamCount();
            int roundsWithBye = (numTeams % 2 == 0) ? numTeams - 1 : numTeams;
            int totalRounds = roundsWithBye * 2;

            Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, userDivision.getTeamIds());
            List<String> teamIds = new ArrayList<>(userDivision.getTeamIds());
            List<RoundInfo> rounds = FixtureQueryHelper.buildRoundInfosWithPhase(
                    tournamentState.getFixtures(), teamNames, teamIds, totalRounds, roundsWithBye, career.getCareerId());

            List<TeamInfo> teamsList = userDivision.getTeamIds().stream()
                    .map(teamId -> career.getSessionTeam(teamId))
                    .filter(Objects::nonNull)
                    .map(t -> new TeamInfo(t.getSessionTeamId(), t.getName()))
                    .toList();

            DivisionConfig config = new DivisionConfig(numTeams, numTeams / 2, numTeams % 2 != 0, roundsWithBye, roundsWithBye);
            return new AllFixturesResponse(career.getCareerId(), totalRounds, rounds, teamsList, teamNames, config);
        });
    }

    private AllFixturesResponse createEmptyResponse() {
        return new AllFixturesResponse("", 0, List.of(), List.of(), Map.of(), new DivisionConfig(0, 0, false, 0, 0));
    }

    // UX-6: BYE indicator — single round with bye info
    public Mono<RoundFixturesWithBye> getRoundWithBye(CareerSave career, int round) {
        return Mono.fromCallable(() -> {
            Division userDivision = career.getUserDivision();
            if (userDivision == null) {
                return new RoundFixturesWithBye(round, List.of(), null);
            }

            TournamentState tournamentState = career.getTournamentState();
            List<MatchFixture> fixtures = tournamentState.getFixturesForRound(round);
            Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, userDivision.getTeamIds());
            List<String> teamIds = new ArrayList<>(userDivision.getTeamIds());

            List<MatchInfo> matches = fixtures.stream().map(f -> FixtureQueryHelper.toMatchInfo(f, teamNames, career.getCareerId())).toList();
            String byeTeam = FixtureQueryHelper.findByeTeam(fixtures, teamIds, teamNames);
            return new RoundFixturesWithBye(round, matches, byeTeam);
        });
    }

    // UX-6: BYE indicator — all rounds with bye info
    public Mono<AllRoundsWithBye> getAllRoundsWithBye(CareerSave career) {
        return Mono.fromCallable(() -> {
            Division userDivision = career.getUserDivision();
            if (userDivision == null) {
                return new AllRoundsWithBye(List.of());
            }

            TournamentState tournamentState = career.getTournamentState();
            int numTeams = userDivision.getTeamCount();
            int roundsWithBye = (numTeams % 2 == 0) ? numTeams - 1 : numTeams;
            int totalRounds = roundsWithBye * 2;

            Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, userDivision.getTeamIds());
            List<String> teamIds = new ArrayList<>(userDivision.getTeamIds());

            List<RoundFixturesWithBye> rounds = new ArrayList<>();
            for (int r = 1; r <= totalRounds; r++) {
                final int currentRound = r;
                List<MatchFixture> roundFixtures = tournamentState.getFixtures().stream()
                        .filter(f -> f.getRound() == currentRound)
                        .toList();
                List<MatchInfo> matches = roundFixtures.stream()
                        .map(f -> FixtureQueryHelper.toMatchInfo(f, teamNames, career.getCareerId()))
                        .toList();
                String byeTeam = FixtureQueryHelper.findByeTeam(roundFixtures, teamIds, teamNames);
                rounds.add(new RoundFixturesWithBye(currentRound, matches, byeTeam));
            }
            return new AllRoundsWithBye(rounds);
        });
    }
}
