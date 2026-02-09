package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.valueobject.MatchFixture;
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

            return FixtureQueryHelper.toMatchInfoList(fixtures, teamNames);
        });
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
                    tournamentState.getFixtures(), teamNames, teamIds, totalRounds, roundsWithBye);

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
}
