package com.footballmanager.application.service.simulation;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.career.CareerNotificationService;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.fixture.CareerFixtureService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerSeasonManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.service.FixtureGenerator;
import com.footballmanager.domain.service.FixtureValidator;
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
 * C55.7.5 — End-to-end smoke combining the real
 * {@link CareerFixtureService#setupCareerFixtures} (production start-career
 * code path) with {@link MatchSimulationOrchestrator#processMatchDayResults}
 * (production round-end code path).
 *
 * <p>C55.7.5 #28 source: REVISOR C55.7.4 reported "Tabla Final con stats en
 * cero" — all 5 SEGUNDA teams showed PJ=0 / G=0 / PTS=0 in the "Torneo
 * Finalizado" page after playing R1-R10.
 *
 * <p>This test pins the BACKEND data-flow contract: after
 * {@code setupCareerFixtures} + a single {@code processMatchDayResults} call
 * with a real win, the standings MUST reflect the result. If this test
 * passes, the backend data flow is correct and the bug is elsewhere
 * (most likely frontend cache or display).
 */
@ExtendWith(MockitoExtension.class)
class C55_7_5_FullEndOfTournamentFlowTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000c575");
    private static final String USER_ID_STR = USER_ID.toString();

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
    private CareerFixtureService careerFixtureService;

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
        careerFixtureService = new CareerFixtureService(new FixtureGenerator(new FixtureValidator()));

        lenient().when(careerSessionService.saveCareer(any(CareerSave.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    /**
     * Build a 5-team SEGUNDA career using the real
     * {@link CareerFixtureService#setupCareerFixtures}.
     */
    private CareerSave makeRealCareer(String userTeamId) {
        CareerSave career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId(userTeamId);

        CareerTeamManager teamManager = new CareerTeamManager();
        String[] teamIds = new String[]{
                userTeamId,
                "00000000-0000-0000-0000-00000000bb01",
                "00000000-0000-0000-0000-00000000cc01",
                "00000000-0000-0000-0000-00000000dd01",
                "00000000-0000-0000-0000-00000000ee01"
        };
        for (String id : teamIds) {
            SessionTeam st = new SessionTeam();
            st.setSessionTeamId(id);
            st.setName("Team-" + id.substring(id.length() - 4));
            teamManager.addSessionTeam(st);
        }
        career.setTeamManager(teamManager);

        CareerPlayerManager playerManager = new CareerPlayerManager();
        playerManager.setSessionPlayers(new HashMap<>());
        playerManager.setFreePlayers(List.of());
        career.setPlayerManager(playerManager);

        CareerSeasonManager seasonManager = new CareerSeasonManager();
        Division division = new Division("SEGUNDA", 2);
        division.setDivisionId("div-segunda-real");
        division.setTeamIds(new ArrayList<>(List.of(teamIds)));
        seasonManager.setDivisions(new ArrayList<>(List.of(division)));
        career.setSeasonManager(seasonManager);

        // REAL initialization (same code as production start-career).
        careerFixtureService.setupCareerFixtures(career, true);
        return career;
    }

    @Test
    @DisplayName("C55.7.5 #28: real setupCareerFixtures + single match win populates standings")
    void realFlow_singleMatch_populatesStandings() {
        String userTeamId = "00000000-0000-0000-0000-00000000aaaa";
        CareerSave career = makeRealCareer(userTeamId);
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        // Sanity: standings were initialized for all 5 teams.
        Map<String, TeamStandings> initial = career.getTournamentState().getStandings();
        assertEquals(5, initial.size(), "All 5 SEGUNDA teams must be in the initial standings Map");

        // Process R1: find the user's match and process it as a 2-1 win.
        TournamentState state = career.getTournamentState();
        assertEquals(10, state.getTotalRounds(), "5 teams in SEGUNDA = 10 total rounds");

        final String userFixtureId;
        {
            String foundId = null;
            for (var fixture : state.getFixtures()) {
                if (userTeamId.equals(fixture.getHomeTeamId())
                        || userTeamId.equals(fixture.getAwayTeamId())) {
                    foundId = fixture.getMatchId();
                    break;
                }
            }
            userFixtureId = foundId;
        }
        assertNotNull(userFixtureId, "User team must have at least one fixture in the career");

        orchestrator.processMatchDayResults(USER_ID_STR, List.of(
                new MatchResultProcessor.MatchResultInfo(userFixtureId, 2, 1)
        )).block(Duration.ofSeconds(5));

        // C55.7.5 #28 CORE ASSERTION: the user's team must have non-zero
        // stats after one match win. The opponent (the other team in the
        // match) must also have non-zero stats (lost, GF=1, GA=2).
        Map<String, TeamStandings> afterR1 = career.getTournamentState().getStandings();
        TeamStandings userStanding = afterR1.get(userTeamId);
        assertNotNull(userStanding);
        assertEquals(1, userStanding.getPlayed(), "User team played=1 after R1 win");
        assertEquals(1, userStanding.getWon(), "User team won=1 after R1 win");
        assertEquals(3, userStanding.getPoints(), "User team PTS=3 after R1 win");
        assertEquals(2, userStanding.getGoalsFor(), "User team GF=2 after R1 win");
        assertEquals(1, userStanding.getGoalsAgainst(), "User team GA=1 after R1 win");

        // The opponent in the user's match: stats should reflect the loss.
        // Find the opponent by looking at the fixture.
        var userFixture = state.getFixtures().stream()
                .filter(f -> f.getMatchId().equals(userFixtureId))
                .findFirst()
                .orElseThrow();
        String opponentId = userTeamId.equals(userFixture.getHomeTeamId())
                ? userFixture.getAwayTeamId()
                : userFixture.getHomeTeamId();
        TeamStandings oppStanding = afterR1.get(opponentId);
        assertNotNull(oppStanding, "Opponent must be in standings Map");
        assertEquals(1, oppStanding.getPlayed(), "Opponent played=1 after R1 loss");
        assertEquals(0, oppStanding.getWon(), "Opponent won=0 after R1 loss");
        assertEquals(1, oppStanding.getLost(), "Opponent lost=1 after R1 loss");
        assertEquals(0, oppStanding.getPoints(), "Opponent PTS=0 after R1 loss");
        assertEquals(1, oppStanding.getGoalsFor(), "Opponent GF=1 after R1 loss");
        assertEquals(2, oppStanding.getGoalsAgainst(), "Opponent GA=2 after R1 loss");
    }

    @Test
    @DisplayName("C55.7.5 #28: divisionFinalStandings populated after determineDivisionChampions")
    void realFlow_divisionFinalStandings_populated() {
        String userTeamId = "00000000-0000-0000-0000-00000000aaaa";
        CareerSave career = makeRealCareer(userTeamId);
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        // Process R1: find the user's match. The user might be home or away
        // depending on the round-robin generator, so we send the result
        // with the user's goals as the appropriate side (homeGoals if user
        // is home, awayGoals if user is away). This makes the test
        // deterministic regardless of fixture generation.
        TournamentState state = career.getTournamentState();
        var userFixture = state.getFixtures().stream()
                .filter(f -> userTeamId.equals(f.getHomeTeamId())
                        || userTeamId.equals(f.getAwayTeamId()))
                .findFirst()
                .orElseThrow();
        boolean userIsHome = userTeamId.equals(userFixture.getHomeTeamId());
        int homeGoals = userIsHome ? 3 : 0;
        int awayGoals = userIsHome ? 0 : 3;

        orchestrator.processMatchDayResults(USER_ID_STR, List.of(
                new MatchResultProcessor.MatchResultInfo(userFixture.getMatchId(), homeGoals, awayGoals)
        )).block(Duration.ofSeconds(5));

        // C55.7.5 #28: divisionFinalStandings (used by PromotionCalculator
        // and the "Tabla Final" page) must reflect the user's stats after
        // at least one match is played.
        List<TeamStandings> finalStandings = state.getDivisionFinalStandings("div-segunda-real");
        assertFalse(finalStandings.isEmpty(),
                "divisionFinalStandings must be populated by determineDivisionChampions "
                        + "even mid-tournament (not just at end)");
        assertEquals(5, finalStandings.size(),
                "All 5 SEGUNDA teams must be in divisionFinalStandings");

        // The user team must be at the top of the standings (3 PTS from
        // the win we just processed).
        TeamStandings topTeam = finalStandings.get(0);
        assertEquals(userTeamId, topTeam.getTeamId(),
                "User team (3 PTS) must be at the top of divisionFinalStandings");
        assertEquals(3, topTeam.getPoints(),
                "Top team PTS=3 after the user's 3-0 win");
        assertEquals(1, topTeam.getPlayed(), "Top team played=1");
        assertEquals(1, topTeam.getWon(), "Top team won=1");
    }
}
