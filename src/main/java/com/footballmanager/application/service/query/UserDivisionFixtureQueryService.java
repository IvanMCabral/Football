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
            // V24D24.3-FIX: build teamNames from the actual fixtures of this round so
            // cross-division fixtures injected via test-harness replaceFixtures resolve
            // to real names instead of falling back to UUIDs (BUG_FIXTURES_TEAM_NAMES_UUID_V2).
            Set<String> teamIdsInFixtures = FixtureQueryHelper.extractTeamIdsFromFixtures(fixtures);
            Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, teamIdsInFixtures);

            return fixtures.stream()
                .map(f -> FixtureQueryHelper.toMatchInfo(f, teamNames, career))
                .toList();
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
            // V24D24.3-FIX: extend teamNames with any cross-division teams present in the
            // tournament fixtures — defensive merge so getAll() resolves names for ALL matches
            // returned, not just user-division matches (BUG_FIXTURES_TEAM_NAMES_UUID_V2).
            Set<String> allFixtureTeamIds = FixtureQueryHelper.extractTeamIdsFromFixtures(tournamentState.getFixtures());
            Map<String, String> extraNames = FixtureQueryHelper.buildTeamNamesMap(career, allFixtureTeamIds);
            teamNames.putAll(extraNames);
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

    /**
     * V25D78-C55.7.7 BUG-M4: getAll with a single-round filter. Returns an
     * {@link AllFixturesResponse} shape with only the requested round in the
     * rounds array (other rounds are filtered out, but {@code teamNames},
     * {@code teams}, and {@code config} stay complete so the UI can still
     * resolve BYE team names + cross-division team names).
     *
     * <p>Strategy decision (per F0 hallazgos + salvedad Mavis #2): instead of
     * in-memory post-filter on the full payload (which works since dataset is
     * bounded at 240 matches = 10 rounds × 24 matches for 5-team divisions),
     * we delegate to {@link #getByRound} for the matches and reconstruct the
     * AllFixturesResponse with a single RoundInfo. This reuses the same code
     * path the existing {@code GET /fixtures?round=N} endpoint uses, so the
     * teamName resolution behavior stays consistent.
     *
     * <p>For rounds with no fixtures (e.g. user requested round=99 of a 10-round
     * tournament) → returns empty matches list but populated teamNames/config.
     */
    public Mono<AllFixturesResponse> getAllByRound(CareerSave career, int round) {
        return getByRound(career, round)
            .map(matches -> buildSingleRoundResponse(career, round, matches));
    }

    private AllFixturesResponse buildSingleRoundResponse(CareerSave career, int round, List<MatchInfo> matches) {
        Division userDivision = career.getUserDivision();
        if (userDivision == null) {
            return new AllFixturesResponse(
                career.getCareerId() != null ? career.getCareerId() : "",
                0, List.of(), List.of(), Map.of(),
                new DivisionConfig(0, 0, false, 0, 0));
        }

        int numTeams = userDivision.getTeamCount();
        int roundsWithBye = (numTeams % 2 == 0) ? numTeams - 1 : numTeams;
        int totalRounds = roundsWithBye * 2;

        Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, userDivision.getTeamIds());
        // Same defensive merge as getAll — extend with cross-division teams from
        // any fixture in the tournament (not just this round), so the teamNames
        // map stays useful even when the filter limits rounds.
        Set<String> allFixtureTeamIds = FixtureQueryHelper.extractTeamIdsFromFixtures(
            career.getTournamentState().getFixtures());
        Map<String, String> extraNames = FixtureQueryHelper.buildTeamNamesMap(career, allFixtureTeamIds);
        teamNames.putAll(extraNames);

        List<TeamInfo> teamsList = userDivision.getTeamIds().stream()
            .map(teamId -> career.getSessionTeam(teamId))
            .filter(Objects::nonNull)
            .map(t -> new TeamInfo(t.getSessionTeamId(), t.getName()))
            .toList();

        DivisionConfig config = new DivisionConfig(numTeams, numTeams / 2, numTeams % 2 != 0, roundsWithBye, roundsWithBye);

        // Phase label: IDA rounds 1..roundsWithBye, VUELTA rounds roundsWithBye+1..totalRounds
        String phase = round <= roundsWithBye ? "IDA" : "VUELTA";
        String phaseLabel = round <= roundsWithBye ? "Primera Vuelta" : "Segunda Vuelta";

        // BYE team: any user-division team not in the matches for this round
        String byeTeam = null;
        if (numTeams % 2 != 0) {
            Set<String> teamsInRound = new HashSet<>();
            matches.forEach(m -> {
                if (m.homeTeamId() != null) teamsInRound.add(m.homeTeamId());
                if (m.awayTeamId() != null) teamsInRound.add(m.awayTeamId());
            });
            for (String teamId : userDivision.getTeamIds()) {
                if (!teamsInRound.contains(teamId)) {
                    byeTeam = teamNames.getOrDefault(teamId, teamId);
                    break;
                }
            }
        }

        RoundInfo roundInfo = new RoundInfo(round, phase, phaseLabel, matches, byeTeam, matches.size());
        return new AllFixturesResponse(
            career.getCareerId() != null ? career.getCareerId() : "",
            totalRounds,
            List.of(roundInfo),
            teamsList,
            teamNames,
            config);
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
            // V24D24.3-FIX: include cross-division teams from this round's fixtures
            // (BUG_FIXTURES_TEAM_NAMES_UUID_V2). user-division set ⊂ fixture set,
            // so this is a superset and never shrinks the map.
            Set<String> teamIdsInFixtures = FixtureQueryHelper.extractTeamIdsFromFixtures(fixtures);
            Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, teamIdsInFixtures);
            List<String> teamIds = new ArrayList<>(userDivision.getTeamIds());

            List<MatchInfo> matches = fixtures.stream().map(f -> FixtureQueryHelper.toMatchInfo(f, teamNames, career)).toList();
            String byeTeam = FixtureQueryHelper.findByeTeam(fixtures, teamIds, teamNames);
            return new RoundFixturesWithBye(round, matches, byeTeam);
        });
    }

    // UX-6: BYE indicator — all rounds with bye info
    public Mono<AllRoundsWithBye> getAllRoundsWithBye(CareerSave career) {
        return Mono.fromCallable(() -> {
            Division userDivision = career.getUserDivision();
            if (userDivision == null) {
                return new AllRoundsWithBye(List.of(), null);
            }

            TournamentState tournamentState = career.getTournamentState();
            int numTeams = userDivision.getTeamCount();
            int roundsWithBye = (numTeams % 2 == 0) ? numTeams - 1 : numTeams;
            int totalRounds = roundsWithBye * 2;

            Map<String, String> teamNames = FixtureQueryHelper.buildTeamNamesMap(career, userDivision.getTeamIds());
            // V24D24.3-FIX: extend teamNames with cross-division teams present in any round
            // (BUG_FIXTURES_TEAM_NAMES_UUID_V2). One career-wide map, not per-round, so a
            // single round with cross-division fixtures doesn't leak UUIDs.
            Set<String> allFixtureTeamIds = FixtureQueryHelper.extractTeamIdsFromFixtures(tournamentState.getFixtures());
            Map<String, String> extraNames = FixtureQueryHelper.buildTeamNamesMap(career, allFixtureTeamIds);
            teamNames.putAll(extraNames);
            List<String> teamIds = new ArrayList<>(userDivision.getTeamIds());

            List<RoundFixturesWithBye> rounds = new ArrayList<>();
            for (int r = 1; r <= totalRounds; r++) {
                final int currentRound = r;
                List<MatchFixture> roundFixtures = tournamentState.getFixtures().stream()
                        .filter(f -> f.getRound() == currentRound)
                        .toList();
                List<MatchInfo> matches = roundFixtures.stream()
                        .map(f -> FixtureQueryHelper.toMatchInfo(f, teamNames, career))
                        .toList();
                String byeTeam = FixtureQueryHelper.findByeTeam(roundFixtures, teamIds, teamNames);
                rounds.add(new RoundFixturesWithBye(currentRound, matches, byeTeam));
            }
            return new AllRoundsWithBye(rounds, career.getUserSessionTeamId());
        });
    }
}
