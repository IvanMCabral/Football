package com.footballmanager.application.service.simulation;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerNotificationService;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * C55.7.5 — Tests for end-of-tournament standings aggregation (HIGH bug #28).
 *
 * <p>REVISOR C55.7.4 smoke: after playing a full season (R1-R10 with
 * teamsPerDivision=5), the "Tabla Final" page showed ALL teams with
 * PJ=0, G=0, E=0, P=0, GF=0, GC=0, DG=0, PTS=0. Mid-season standings
 * (R3) were correct.
 *
 * <p>These tests pin the contract that {@code MatchSimulationOrchestrator}
 * aggregates match results into {@code TournamentState.standings} over
 * multiple rounds, AND that the final standings have non-zero stats
 * after the last round (which triggers {@code finishTournament}).
 */
@ExtendWith(MockitoExtension.class)
class C55_7_5_EndOfTournamentAggregationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000c575");
    private static final String USER_ID_STR = USER_ID.toString();
    private static final String USER_TEAM = "team-user";
    private static final String RIVAL_A = "team-rival-a";
    private static final String RIVAL_B = "team-rival-b";

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
    private MatchSimulationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        resultProcessor = new MatchResultProcessor();
        orchestrator = new MatchSimulationOrchestrator(
                careerRepository,
                careerSessionService,
                notificationService,
                resultProcessor,
                org.mockito.Mockito.mock(com.footballmanager.application.service.simulation.LeagueSimulator.class),
                roundEngineRegistry);

        lenient().when(careerSessionService.saveCareer(any(CareerSave.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    /**
     * Build a 2-round career with 3 teams and 1 user-fixture per round.
     * Round 1: USER_TEAM vs RIVAL_A (USER_TEAM plays, RIVAL_B has a BYE).
     * Round 2: USER_TEAM vs RIVAL_B (USER_TEAM plays, RIVAL_A has a BYE).
     */
    private CareerSave makeCareer() {
        CareerSave career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId(USER_TEAM);

        CareerSeasonManager seasonManager = new CareerSeasonManager();
        // C55.7.5: determineDivisionChampions iterates over divisions. We need
        // a real Division with user team in its teamIds so the filter works.
        Division division = new Division("SEGUNDA", 2);
        division.setDivisionId("div-segunda-test");
        division.setTeamIds(new ArrayList<>(List.of(USER_TEAM, RIVAL_A, RIVAL_B)));
        seasonManager.setDivisions(new ArrayList<>(List.of(division)));
        career.setSeasonManager(seasonManager);

        career.setTeamManager(new CareerTeamManager());

        CareerPlayerManager playerManager = new CareerPlayerManager();
        playerManager.setSessionPlayers(new HashMap<>());
        playerManager.setFreePlayers(List.of());
        career.setPlayerManager(playerManager);

        TournamentState state = new TournamentState();
        state.setCurrentRound(1);
        state.setTotalRounds(2);

        // R1: USER_TEAM vs RIVAL_A
        state.getFixtures().add(new MatchFixture("match-r1-1", USER_TEAM, RIVAL_A, 1));
        // R1 BYE: RIVAL_B vs RIVAL_B (placeholder so R1 has a fixture set; not played by user)
        // Actually for BYE we don't add a fixture. Just R2: USER_TEAM vs RIVAL_B.
        state.getFixtures().add(new MatchFixture("match-r2-1", USER_TEAM, RIVAL_B, 2));

        career.setTournamentState(state);

        return career;
    }

    private void seedStandings(CareerSave career, String... teamIds) {
        Map<String, TeamStandings> standings = new HashMap<>();
        for (String teamId : teamIds) {
            standings.put(teamId, new TeamStandings(teamId, teamId + " FC"));
        }
        career.getTournamentState().setStandings(standings);
    }

    @Test
    @DisplayName("C55.7.5 #28: processMatchDayResults accumulates standings over multiple rounds")
    void processMatchDayResults_accumulatesStandings_acrossRounds() {
        // Career at R1, totalRounds=2. R1 fixture involves USER_TEAM.
        // After R1, currentRound should advance to 2; after R2 (last round),
        // finishTournament should set finished=true.
        CareerSave career = makeCareer();
        seedStandings(career, USER_TEAM, RIVAL_A, RIVAL_B);
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        // R1: USER_TEAM wins 2-0 vs RIVAL_A
        orchestrator.processMatchDayResults(USER_ID_STR, List.of(
                new MatchResultProcessor.MatchResultInfo("match-r1-1", 2, 0)
        )).block(Duration.ofSeconds(5));

        // After R1: USER_TEAM has PTS=3 (won), RIVAL_A has PTS=0 (lost).
        // RIVAL_B should still be at 0 (not played).
        Map<String, TeamStandings> afterR1 = career.getTournamentState().getStandings();
        assertEquals(1, afterR1.get(USER_TEAM).getPlayed(), "USER_TEAM played=1 after R1");
        assertEquals(1, afterR1.get(USER_TEAM).getWon(), "USER_TEAM won=1 after R1");
        assertEquals(3, afterR1.get(USER_TEAM).getPoints(), "USER_TEAM PTS=3 after R1");
        assertEquals(2, afterR1.get(USER_TEAM).getGoalsFor(), "USER_TEAM GF=2 after R1");
        assertEquals(0, afterR1.get(USER_TEAM).getGoalsAgainst(), "USER_TEAM GA=0 after R1");

        assertEquals(1, afterR1.get(RIVAL_A).getPlayed(), "RIVAL_A played=1 after R1");
        assertEquals(0, afterR1.get(RIVAL_A).getPoints(), "RIVAL_A PTS=0 after R1 (lost)");
        assertEquals(0, afterR1.get(RIVAL_A).getGoalsFor(), "RIVAL_A GF=0 after R1");
        assertEquals(2, afterR1.get(RIVAL_A).getGoalsAgainst(), "RIVAL_A GA=2 after R1");

        assertEquals(0, afterR1.get(RIVAL_B).getPlayed(), "RIVAL_B played=0 after R1 (BYE)");

        // R2: USER_TEAM draws 1-1 vs RIVAL_B
        orchestrator.processMatchDayResults(USER_ID_STR, List.of(
                new MatchResultProcessor.MatchResultInfo("match-r2-1", 1, 1)
        )).block(Duration.ofSeconds(5));

        Map<String, TeamStandings> afterR2 = career.getTournamentState().getStandings();
        // CUMULATIVE after R2: USER_TEAM = 2G, 1W, 0D, 0L, GF=3, GA=1, PTS=6
        assertEquals(2, afterR2.get(USER_TEAM).getPlayed(), "USER_TEAM played=2 after R2");
        assertEquals(1, afterR2.get(USER_TEAM).getWon(), "USER_TEAM won=1 after R2");
        assertEquals(1, afterR2.get(USER_TEAM).getDrawn(), "USER_TEAM drawn=1 after R2");
        assertEquals(4, afterR2.get(USER_TEAM).getPoints(), "USER_TEAM PTS=4 after R2 (W=3+D=1)");
        assertEquals(3, afterR2.get(USER_TEAM).getGoalsFor(), "USER_TEAM GF=3 cumulative after R2");
        assertEquals(1, afterR2.get(USER_TEAM).getGoalsAgainst(), "USER_TEAM GA=1 cumulative after R2");

        // C55.7.5 #28: After R2 (last round), finishTournament must have set finished=true.
        assertTrue(career.getTournamentState().getFinished(), "Tournament must be marked finished after last round");
        assertEquals(CareerPhase.FINISHED, career.getTournamentState().getCareerPhase(),
                "Phase must be FINISHED after last round");
    }

    @Test
    @DisplayName("C55.7.5 #28: divisionFinalStandings populated with sorted standings at end-of-tournament")
    void divisionFinalStandings_populatedAtEndOfTournament() {
        // C55.7.5 #28: REVISOR observed "Tabla Final con stats en cero" — the
        // frontend table reads from divisionFinalStandings via the PromotionCalculator
        // or via getStandings endpoint. After finishTournament, this Map MUST
        // contain the cumulative standings (not 0/0/0).
        CareerSave career = makeCareer();
        seedStandings(career, USER_TEAM, RIVAL_A, RIVAL_B);
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        // R1: USER_TEAM wins 3-1 vs RIVAL_A
        orchestrator.processMatchDayResults(USER_ID_STR, List.of(
                new MatchResultProcessor.MatchResultInfo("match-r1-1", 3, 1)
        )).block(Duration.ofSeconds(5));

        // R2: USER_TEAM loses 0-2 vs RIVAL_B
        orchestrator.processMatchDayResults(USER_ID_STR, List.of(
                new MatchResultProcessor.MatchResultInfo("match-r2-1", 0, 2)
        )).block(Duration.ofSeconds(5));

        // C55.7.5 #28: divisionFinalStandings for the user division MUST be
        // populated with teams that have NON-ZERO stats.
        Division userDiv = career.getSeasonManager().getDivisions().get(0);
        List<TeamStandings> finalStandings = career.getTournamentState()
                .getDivisionFinalStandings(userDiv.getDivisionId());

        assertFalse(finalStandings.isEmpty(), "divisionFinalStandings must not be empty after end-of-tournament");

        // Find USER_TEAM and RIVAL_B in the final standings.
        TeamStandings userStanding = finalStandings.stream()
                .filter(s -> USER_TEAM.equals(s.getTeamId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("USER_TEAM must be in divisionFinalStandings"));
        TeamStandings rivalBStanding = finalStandings.stream()
                .filter(s -> RIVAL_B.equals(s.getTeamId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("RIVAL_B must be in divisionFinalStandings"));

        // C55.7.5 #28: stats must be non-zero. USER_TEAM played 2 (W=1, L=1, GF=3, GA=3, PTS=3).
        assertEquals(2, userStanding.getPlayed(), "USER_TEAM played=2 in final standings");
        assertEquals(3, userStanding.getPoints(), "USER_TEAM PTS=3 in final standings (1W + 1L = 3 + 0)");
        assertEquals(3, userStanding.getGoalsFor(), "USER_TEAM GF=3 in final standings");
        assertEquals(3, userStanding.getGoalsAgainst(), "USER_TEAM GA=3 in final standings");

        // RIVAL_B played 1 (W=1, GF=2, GA=0, PTS=3).
        assertEquals(1, rivalBStanding.getPlayed(), "RIVAL_B played=1 in final standings");
        assertEquals(3, rivalBStanding.getPoints(), "RIVAL_B PTS=3 in final standings (1W)");
        assertEquals(2, rivalBStanding.getGoalsFor(), "RIVAL_B GF=2 in final standings");
        assertEquals(0, rivalBStanding.getGoalsAgainst(), "RIVAL_B GA=0 in final standings");

        // C55.7.5 #28: RIVAL_B (more PTS) should be ranked above USER_TEAM.
        // Sort: PTS desc, then GD, then GF.
        int rivalBIdx = finalStandings.indexOf(rivalBStanding);
        int userIdx = finalStandings.indexOf(userStanding);
        assertTrue(rivalBIdx < userIdx,
                "RIVAL_B (PTS=3, GD=+2) must be ranked above USER_TEAM (PTS=3, GD=0). Got rivalBIdx="
                        + rivalBIdx + ", userIdx=" + userIdx);
    }
}
