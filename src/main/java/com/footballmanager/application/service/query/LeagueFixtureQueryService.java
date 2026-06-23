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
 * Servicio de consultas para fixtures de todas las divisiones (liga).
 */
@Service
public class LeagueFixtureQueryService {

    public Mono<CompleteFixturesResponse> getCompleteFixtures(CareerSave career) {
        return Mono.fromCallable(() -> {
            TournamentState mainState = career.getTournamentState();
            List<MatchFixture> allFixtures = mainState.getFixtures();
            int totalRounds = mainState.getTotalRounds();

            // Usar LinkedHashSet para mantener orden y eliminar duplicados por divisionId
            java.util.Set<String> seenDivisions = new java.util.HashSet<>();
            List<DivisionFixtures> divisions = career.getSeasonManager().getDivisions().stream()
                    .filter(division -> {
                        boolean isNew = seenDivisions.add(division.getDivisionId());
                        return isNew;
                    })
                    .map(division -> buildDivisionFixtures(division, allFixtures, totalRounds, career))
                    .toList();

            // Free teams - equipos que no participan en ninguna división
            List<TeamInfo> freeTeams = career.getSeasonManager().getFreeTeams().stream()
                    .map(teamId -> {
                        SessionTeam team = career.getSessionTeam(teamId);
                        String name = team != null ? team.getName() : teamId;
                        return new TeamInfo(teamId, name);
                    })
                    .toList();

            return new CompleteFixturesResponse(
                    career.getCareerId().toString(),
                    mainState.getCurrentRound(),
                    totalRounds,
                    divisions,
                    freeTeams
            );
        });
    }

    public Mono<List<LeagueDivisionFixtures>> getLeagueFixtures(CareerSave career, Integer round) {
        return Mono.fromCallable(() -> {
            int targetRound = round != null ? round : career.getTournamentState().getCurrentRound();
            TournamentState mainState = career.getTournamentState();
            List<MatchFixture> allFixtures = mainState.getFixtures();

            return career.getSeasonManager().getDivisions().stream()
                    .map(division -> buildLeagueDivisionFixtures(division, allFixtures, targetRound, career))
                    .toList();
        });
    }

    private DivisionFixtures buildDivisionFixtures(Division division, List<MatchFixture> allFixtures,
            int totalRounds, CareerSave career) {
        Set<String> divisionTeamIds = new HashSet<>(division.getTeamIds());
        boolean isUserDivision = career.getUserDivision() != null &&
                division.getDivisionId().equals(career.getUserDivision().getDivisionId());

        Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, divisionTeamIds);
        // V24D24.3-FIX (defense in depth): BUG_FIXTURES_TEAM_NAMES_UUID_V2. Although
        // buildRoundInfosSimple filters fixtures to this division, build teamNames from
        // the union of divisionTeamIds AND any teamId appearing in the supplied fixtures,
        // so a future caller that passes cross-division fixtures doesn't leak UUIDs.
        Set<String> fixtureTeamIds = FixtureQueryHelper.extractTeamIdsFromFixtures(allFixtures);
        Map<String, String> extraNames = FixtureQueryHelper.buildTeamNamesMap(career, fixtureTeamIds);
        teamNames.putAll(extraNames);
        List<RoundInfo> rounds = FixtureQueryHelper.buildRoundInfosSimple(allFixtures, teamNames, divisionTeamIds, totalRounds, career.getCareerId());

        return new DivisionFixtures(
                division.getDivisionId().toString(),
                division.getDisplayName(),
                isUserDivision,
                rounds
        );
    }

    private LeagueDivisionFixtures buildLeagueDivisionFixtures(Division division,
            List<MatchFixture> allFixtures, int targetRound, CareerSave career) {
        Set<String> divisionTeamIds = new HashSet<>(division.getTeamIds());
        Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, divisionTeamIds);

        List<MatchFixture> fixtures = allFixtures.stream()
                .filter(f -> f.getRound() == targetRound)
                .filter(f -> divisionTeamIds.contains(f.getHomeTeamId()) && divisionTeamIds.contains(f.getAwayTeamId()))
                .toList();

        // V24D24.3-FIX (defense in depth): BUG_FIXTURES_TEAM_NAMES_UUID_V2.
        // Extend teamNames from the actual filtered fixtures so even if the division
        // filter above is loosened in the future, every team in the response resolves.
        Set<String> fixtureTeamIds = FixtureQueryHelper.extractTeamIdsFromFixtures(fixtures);
        Map<String, String> extraNames = FixtureQueryHelper.buildTeamNamesMap(career, fixtureTeamIds);
        teamNames.putAll(extraNames);

        List<LeagueMatchInfo> matches = fixtures.stream().map(f -> {
            int homeOvr = calculateSessionTeamOvr(career, f.getHomeTeamId());
            int awayOvr = calculateSessionTeamOvr(career, f.getAwayTeamId());
            var lambdas = com.footballmanager.application.service.domain.MatchQualityComputer.computeLambdas(homeOvr, awayOvr);
            var metrics = com.footballmanager.domain.model.valueobject.MatchQualityMetrics.fromLambdas(lambdas);
            return new LeagueMatchInfo(
                    f.getMatchId().toString(),
                    f.getHomeTeamId(),
                    teamNames.get(f.getHomeTeamId()),
                    f.getAwayTeamId(),
                    teamNames.get(f.getAwayTeamId()),
                    f.getRound(),
                    f.getStatus().name(),
                    f.getResult() != null ? f.getResult().getHomeGoals() : null,
                    f.getResult() != null ? f.getResult().getAwayGoals() : null,
                    metrics.homeXg(),
                    metrics.awayXg(),
                    metrics.totalXg(),
                    FixtureQueryHelper.deriveRoundId(career.getCareerId(), f.getRound())
            );
        }).toList();

        return new LeagueDivisionFixtures(
                division.getDivisionId().toString(),
                division.getDisplayName(),
                career.getUserDivision() != null && division.getDivisionId().equals(career.getUserDivision().getDivisionId()),
                matches,
                division.hasByeInRound(targetRound)
        );
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
}
