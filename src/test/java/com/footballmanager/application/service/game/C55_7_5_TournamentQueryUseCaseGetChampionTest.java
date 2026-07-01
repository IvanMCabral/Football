package com.footballmanager.application.service.game;

import com.footballmanager.adapters.in.web.game.dto.ChampionDTO;
import com.footballmanager.adapters.in.web.game.dto.StandingDTO;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerSeasonManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.repository.CareerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * C55.7.5 — Tests for {@link TournamentQueryUseCaseImpl#getChampion(String)}
 * (HIGH bug #29 from C55.7.4).
 *
 * <p>REVISOR C55.7.4 smoke: "¡Torneo Finalizado!" page shows
 * "Error al cargar el campeón" subtitle. Root cause:
 * {@code getChampion} returned {@code Mono.empty()} when the top team's
 * points were 0, which happened when bug #28 left standings with zero
 * stats OR when the career was fresh with no played matches.
 *
 * <p>The previous contract:
 * <pre>
 *   if (standings.isEmpty() || standings.get(0).points() == 0) {
 *       return Mono.empty();
 *   }
 * </pre>
 * was incorrect — the champion endpoint should return a valid DTO for
 * the top-ranked team using the proper tiebreaker (PTS &gt; GD &gt; GF),
 * not skip just because the leading team hasn't earned points yet.
 */
@ExtendWith(MockitoExtension.class)
class C55_7_5_TournamentQueryUseCaseGetChampionTest {

    private static final String USER_ID = "00000000-0000-0000-0000-00000000c575";
    private static final String TEAM_VILLARREAL = "00000000-0000-0000-0000-00000000aaaa";
    private static final String TEAM_ATLETICO = "00000000-0000-0000-0000-00000000bbbb";
    private static final String TEAM_BARCELONA = "00000000-0000-0000-0000-00000000cccc";

    @Mock
    private CareerRepository careerRepository;

    private TournamentQueryUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new TournamentQueryUseCaseImpl(careerRepository);
    }

    /**
     * Build a CareerSave with N teams in a SEGUNDA division. Each team starts
     * with 0 stats. Optionally seed standings with provided stats for the
     * "tournament finished, all-zero" scenario.
     */
    private CareerSave makeCareer(boolean finished, java.util.Map<String, int[]> statsByTeamId) {
        CareerSave career = new CareerSave();
        career.setUserId(java.util.UUID.fromString(USER_ID));

        CareerSeasonManager seasonManager = new CareerSeasonManager();
        Division division = new Division("SEGUNDA", 2);
        division.setDivisionId("div-segunda-test");
        List<String> teamIds = new ArrayList<>(statsByTeamId.keySet());
        division.setTeamIds(teamIds);
        seasonManager.setDivisions(new ArrayList<>(List.of(division)));
        career.setSeasonManager(seasonManager);

        career.setTeamManager(new CareerTeamManager());
        CareerPlayerManager pm = new CareerPlayerManager();
        pm.setSessionPlayers(new HashMap<>());
        pm.setFreePlayers(List.of());
        career.setPlayerManager(pm);

        TournamentState state = new TournamentState();
        state.setFinished(finished);
        HashMap<String, TeamStandings> standings = new HashMap<>();
        for (var entry : statsByTeamId.entrySet()) {
            String teamId = entry.getKey();
            int[] stats = entry.getValue();
            // stats = [played, won, drawn, lost, gf, ga, points]
            TeamStandings ts = new TeamStandings(teamId, teamId + " FC");
            ts.setPlayed(stats[0]);
            ts.setWon(stats[1]);
            ts.setDrawn(stats[2]);
            ts.setLost(stats[3]);
            ts.setGoalsFor(stats[4]);
            ts.setGoalsAgainst(stats[5]);
            ts.setPoints(stats[6]);
            ts.setGoalDifference(stats[4] - stats[5]);
            standings.put(teamId, ts);
        }
        state.setStandings(standings);
        career.setTournamentState(state);

        return career;
    }

    @Test
    @DisplayName("C55.7.5 #29: getChampion returns top team even when ALL standings have PTS=0 (empty-results edge case)")
    void getChampion_allStandingsZero_returnsTopByHashOrder() {
        // The buggy contract returned Mono.empty() because standings.get(0).points() == 0.
        // The corrected contract: ALWAYS return the top-ranked team. If all teams have
        // identical stats, fall back to the first entry by deterministic sort.
        CareerSave career = makeCareer(true, new java.util.LinkedHashMap<>(java.util.Map.of(
                TEAM_VILLARREAL, new int[]{0, 0, 0, 0, 0, 0, 0},
                TEAM_ATLETICO, new int[]{0, 0, 0, 0, 0, 0, 0},
                TEAM_BARCELONA, new int[]{0, 0, 0, 0, 0, 0, 0}
        )));
        when(careerRepository.findById(eq(USER_ID))).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getChampion(USER_ID))
                .assertNext(dto -> {
                    assertNotNull(dto, "Champion DTO must not be null even when all teams have PTS=0");
                    // The DTO should carry one of the 3 teams (any of them is valid for the
                    // all-zero case — the point is that the endpoint no longer 404s).
                    assertNotNull(dto.teamId(), "Champion teamId must be populated");
                    assertEquals(0, dto.points(), "Champion points=0 in this all-zero scenario");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("C55.7.5 #29: getChampion returns the leader by points (non-zero data)")
    void getChampion_returnsLeaderByPoints() {
        // Sanity check: with non-zero stats, the champion is the team with the
        // most points. Villarreal = 3 PTS, Atletico = 1 PT, Barcelona = 0 PTS.
        CareerSave career = makeCareer(true, new java.util.LinkedHashMap<>(java.util.Map.of(
                TEAM_BARCELONA, new int[]{1, 0, 0, 1, 0, 2, 0},
                TEAM_VILLARREAL, new int[]{1, 1, 0, 0, 2, 0, 3},
                TEAM_ATLETICO, new int[]{1, 0, 1, 0, 1, 1, 1}
        )));
        when(careerRepository.findById(eq(USER_ID))).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getChampion(USER_ID))
                .assertNext(dto -> {
                    assertNotNull(dto);
                    assertEquals(java.util.UUID.fromString(TEAM_VILLARREAL), dto.teamId(),
                            "Villarreal has 3 PTS and must be champion");
                    assertEquals(3, dto.points());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("C55.7.5 #29: getChampion resolves tie by goalDifference (PTS equal)")
    void getChampion_resolvesTieByGoalDifference() {
        // Both Villarreal and Atletico have 3 PTS. Villarreal GD=+2, Atletico GD=+1.
        // Villarreal should win the tiebreaker.
        CareerSave career = makeCareer(true, new java.util.LinkedHashMap<>(java.util.Map.of(
                TEAM_VILLARREAL, new int[]{1, 1, 0, 0, 3, 1, 3},
                TEAM_ATLETICO, new int[]{1, 1, 0, 0, 2, 1, 3}
        )));
        when(careerRepository.findById(eq(USER_ID))).thenReturn(Mono.just(Optional.of(career)));

        StepVerifier.create(useCase.getChampion(USER_ID))
                .assertNext(dto -> {
                    assertNotNull(dto);
                    assertEquals(java.util.UUID.fromString(TEAM_VILLARREAL), dto.teamId(),
                            "Villarreal wins tie by GD=+2 vs Atletico GD=+1");
                    assertEquals(3, dto.points());
                    assertEquals(2, dto.goalDifference());
                })
                .verifyComplete();
    }
}
