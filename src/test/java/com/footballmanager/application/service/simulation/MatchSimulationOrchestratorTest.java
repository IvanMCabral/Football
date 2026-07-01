package com.footballmanager.application.service.simulation;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerNotificationService;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerSeasonManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.service.MatchSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C55.8 — Tests for {@link MatchSimulationOrchestrator} covering the B1.a
 * (stale/future matchId round-equality guard) and B1.b (BYE-round auto-advance)
 * fixes. The orchestrator was previously silent-rejecting any results whose
 * fixture round did not exactly match career.currentRound, and was returning
 * {@code Mono.empty()} for empty results — both behaviors got the career stuck
 * under multi-division / BYE semantics.
 *
 * <p>Coverage:
 * <ul>
 *   <li>B1.b: empty results + BYE round → auto-advance to next round.</li>
 *   <li>B1.b: empty results + non-BYE round → log error and stay stuck (cannot
 *       safely advance without match data).</li>
 *   <li>B1.a: stale matchId (fixture.round &lt; careerCurrentRound) → idempotent skip.</li>
 *   <li>B1.a: future matchId (fixture.round &gt; careerCurrentRound) → advance career
 *       to fixture.round, then process results normally.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MatchSimulationOrchestratorTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c8");
    private static final String USER_ID_STR = USER_ID.toString();
    private static final String USER_TEAM = "team-user";
    private static final String OTHER_TEAM_A = "team-other-a";
    private static final String OTHER_TEAM_B = "team-other-b";

    @Mock
    private CareerRepository careerRepository;
    @Mock
    private CareerSessionService careerSessionService;
    @Mock
    private CareerNotificationService notificationService;
    @Mock
    private MatchSimulator matchSimulator;
    @Mock
    private RoundEngineRegistry roundEngineRegistry;

    private MatchResultProcessor resultProcessor;
    private LeagueSimulator leagueSimulator;
    private MatchSimulationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        resultProcessor = new MatchResultProcessor();
        leagueSimulator = new LeagueSimulator(matchSimulator);
        orchestrator = new MatchSimulationOrchestrator(
                careerRepository,
                careerSessionService,
                notificationService,
                resultProcessor,
                leagueSimulator,
                roundEngineRegistry);

        // saveCareer is the only mutation contract per CareerSave save rules
        // (see CareerSessionService.saveCareer: writes through to cache + repo).
        // Some tests (non-BYE, stale) never reach saveCareer, so we register the
        // stub leniently to avoid Mockito strict-stubbing complaints.
        lenient().when(careerSessionService.saveCareer(any(CareerSave.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    /**
     * Build a minimal {@link CareerSave} at the requested round, with a
     * configured per-round fixture map. Each fixture is a {@code String[2]}
     * of {@code [homeTeamId, awayTeamId]}.
     */
    private CareerSave makeCareer(int currentRound, int totalRounds,
                                  Map<Integer, List<String[]>> fixturesByRound) {
        CareerSave career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId(USER_TEAM);

        // Empty season manager: tests do not exercise promotions. If a path
        // reaches finishTournament, determineDivisionChampions iterates an empty
        // divisions list and returns an empty result — safe.
        career.setSeasonManager(new CareerSeasonManager());

        CareerTeamManager teamManager = new CareerTeamManager();
        career.setTeamManager(teamManager);

        CareerPlayerManager playerManager = new CareerPlayerManager();
        playerManager.setSessionPlayers(new HashMap<>());
        playerManager.setFreePlayers(List.of());
        career.setPlayerManager(playerManager);

        TournamentState tournamentState = new TournamentState();
        tournamentState.setCurrentRound(currentRound);
        tournamentState.setTotalRounds(totalRounds);

        int matchCounter = 1;
        for (Map.Entry<Integer, List<String[]>> entry : fixturesByRound.entrySet()) {
            int round = entry.getKey();
            for (String[] pair : entry.getValue()) {
                String matchId = "match-r" + round + "-" + matchCounter++;
                tournamentState.getFixtures().add(
                        new MatchFixture(matchId, pair[0], pair[1], round));
            }
        }

        career.setTournamentState(tournamentState);

        return career;
    }

    /** Seed standings for the supplied team ids so {@code updateStandingsWithResult} can run. */
    private void seedStandings(CareerSave career, String... teamIds) {
        Map<String, TeamStandings> standings = new HashMap<>();
        for (String teamId : teamIds) {
            standings.put(teamId, new TeamStandings(teamId, teamId + " FC"));
        }
        career.getTournamentState().setStandings(standings);
    }

    @Test
    @DisplayName("C55.8 B1.b: empty results on a BYE round auto-advance career.currentRound")
    void processMatchDayResults_emptyResults_byeRound_autoAdvancesToNextRound() {
        // Career at R5, totalRounds=10. R5 fixtures do NOT involve USER_TEAM → BYE.
        CareerSave career = makeCareer(5, 10, Map.of(
                5, List.<String[]>of(new String[]{OTHER_TEAM_A, OTHER_TEAM_B})
        ));
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        orchestrator.processMatchDayResults(USER_ID_STR, List.of())
                .block(Duration.ofSeconds(5));

        TournamentState state = career.getTournamentState();
        assertEquals(6, state.getCurrentRound(),
                "BYE round must auto-advance currentRound by 1");
        assertEquals(CareerPhase.WAITING_USER, state.getCareerPhase(),
                "Phase should remain WAITING_USER after BYE advance (no match was played)");
        verify(careerSessionService).saveCareer(career);
        verify(roundEngineRegistry).unregister(USER_ID);
    }

    @Test
    @DisplayName("C55.8 B1.b: empty results on a NON-BYE round log error and stay stuck")
    void processMatchDayResults_emptyResults_notBye_logsErrorAndStaysStuck() {
        // Career at R5, totalRounds=10. R5 fixtures DO involve USER_TEAM → NOT a BYE.
        // Empty results here means the caller misbehaved (e.g. UI bug, partial state).
        // We must NOT silently advance — that would skip the user's match.
        CareerSave career = makeCareer(5, 10, Map.of(
                5, List.<String[]>of(new String[]{USER_TEAM, OTHER_TEAM_A})
        ));
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        orchestrator.processMatchDayResults(USER_ID_STR, List.of())
                .block(Duration.ofSeconds(5));

        TournamentState state = career.getTournamentState();
        assertEquals(5, state.getCurrentRound(),
                "Non-BYE empty results must NOT advance currentRound (would skip user's match)");
        assertEquals(CareerPhase.PRE_MATCH, state.getCareerPhase(),
                "Phase should remain untouched on a non-BYE empty-results path");
        verify(careerSessionService, never()).saveCareer(any());
        verify(roundEngineRegistry, never()).unregister(any());
    }

    @Test
    @DisplayName("C55.8 B1.a: stale matchId (fixture.round < careerCurrentRound) is idempotently skipped")
    void processMatchDayResults_staleMatchId_isIdempotentlySkipped() {
        // Career at R5, totalRounds=10. The caller sends a result for an R3 fixture
        // (stale: it was already processed in a previous round and the orchestrator
        // has advanced past it). Before the fix this caused silent null return.
        CareerSave career = makeCareer(5, 10, Map.of(
                3, List.<String[]>of(new String[]{USER_TEAM, OTHER_TEAM_A})
        ));
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        MatchResultProcessor.MatchResultInfo staleResult =
                new MatchResultProcessor.MatchResultInfo("match-r3-1", 1, 0);

        orchestrator.processMatchDayResults(USER_ID_STR, List.of(staleResult))
                .block(Duration.ofSeconds(5));

        TournamentState state = career.getTournamentState();
        assertEquals(5, state.getCurrentRound(),
                "Stale matchId must NOT advance or disturb currentRound");
        assertEquals(CareerPhase.PRE_MATCH, state.getCareerPhase(),
                "Stale matchId must NOT trigger any phase transition");
        verify(careerSessionService, never()).saveCareer(any());
        verify(roundEngineRegistry, never()).unregister(any());
    }

    @Test
    @DisplayName("C55.8 B1.a: future matchId (fixture.round > careerCurrentRound) advances career then processes")
    void processMatchDayResults_futureMatchId_advancesCareerThenProcesses() {
        // Career at R5, totalRounds=10. Caller sends a result for an R7 fixture
        // (future: career is behind the actual gameplay state). Before the fix
        // this caused silent null return — the user's R7 result was lost.
        CareerSave career = makeCareer(5, 10, Map.of(
                7, List.<String[]>of(new String[]{USER_TEAM, OTHER_TEAM_A})
        ));
        seedStandings(career, USER_TEAM, OTHER_TEAM_A);
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        MatchResultProcessor.MatchResultInfo futureResult =
                new MatchResultProcessor.MatchResultInfo("match-r7-1", 2, 1);

        orchestrator.processMatchDayResults(USER_ID_STR, List.of(futureResult))
                .block(Duration.ofSeconds(5));

        TournamentState state = career.getTournamentState();
        // B1.a fix: setCurrentRound(7), then the normal post-process flow advances
        // from 7 to 8 (currentRound < totalRounds branch).
        assertEquals(8, state.getCurrentRound(),
                "Future matchId should advance career to fixture.round (5→7), then post-process to 7→8");
        assertEquals(CareerPhase.WAITING_USER, state.getCareerPhase(),
                "Phase should end at WAITING_USER after a normal post-match-day flow");

        // The result must have been persisted on the fixture.
        MatchFixture fixture = state.getFixtures().stream()
                .filter(f -> f.getMatchId().equals("match-r7-1"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("R7 fixture must exist"));
        assertNotNull(fixture.getResult(), "R7 fixture must have its result recorded");
        assertEquals(2, fixture.getResult().getHomeGoals());
        assertEquals(1, fixture.getResult().getAwayGoals());

        verify(careerSessionService).saveCareer(career);
        verify(roundEngineRegistry).unregister(USER_ID);
    }
}