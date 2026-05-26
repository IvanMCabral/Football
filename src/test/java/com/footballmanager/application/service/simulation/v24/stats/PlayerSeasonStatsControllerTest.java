package com.footballmanager.application.service.simulation.v24.stats;

import com.footballmanager.adapters.in.web.career.controllers.PlayerSeasonStatsController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V24D6M4: Unit tests for PlayerSeasonStatsController.
 *
 * Uses reactive return types (Mono<ResponseEntity<Object>>) matching
 * the controller's actual signatures. Tests are deterministic and fast,
 * requiring only mocked PlayerSeasonStatsQueryService.
 */
class PlayerSeasonStatsControllerTest {

    private static final String CAREER_ID = "career-123";
    private static final Integer SEASON = 1;

    @Test
    void getAllPlayerStats_returns200WithResponse() {
        PlayerSeasonStatsQueryService queryService = mock(PlayerSeasonStatsQueryService.class);
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.getPlayerSeasonStats(CAREER_ID, SEASON))
                .thenReturn(PlayerSeasonStatsResponse.builder()
                        .careerId(CAREER_ID)
                        .season(SEASON)
                        .playerStats(List.of())
                        .totalGoals(0)
                        .totalAssists(0)
                        .totalAppearances(0)
                        .averageRating(0.0)
                        .incomplete(false)
                        .message("ok")
                        .build());

        PlayerSeasonStatsController controller = new PlayerSeasonStatsController(queryService);
        Mono<ResponseEntity<Object>> result = controller.getPlayerSeasonStats(CAREER_ID, SEASON);

        ResponseEntity<Object> entity = result.block();
        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlayerSeasonStatsResponse body = (PlayerSeasonStatsResponse) entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.careerId()).isEqualTo(CAREER_ID);
        assertThat(body.season()).isEqualTo(SEASON);
    }

    @Test
    void getTeamPlayerStats_filtersTeam() {
        PlayerSeasonStatsQueryService queryService = mock(PlayerSeasonStatsQueryService.class);
        when(queryService.isApiEnabled()).thenReturn(true);
        when(queryService.getPlayerSeasonStats(eq(CAREER_ID), eq(SEASON), eq("team-A"), isNull(), isNull()))
                .thenReturn(PlayerSeasonStatsResponse.builder()
                        .careerId(CAREER_ID)
                        .season(SEASON)
                        .playerStats(List.of())
                        .totalGoals(0)
                        .totalAssists(0)
                        .totalAppearances(0)
                        .averageRating(0.0)
                        .incomplete(false)
                        .message("ok")
                        .build());

        PlayerSeasonStatsController controller = new PlayerSeasonStatsController(queryService);
        Mono<ResponseEntity<Object>> result = controller.getTeamPlayerSeasonStats(CAREER_ID, SEASON, "team-A");

        ResponseEntity<Object> entity = result.block();
        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getSinglePlayerStats_returns200WhenPlayerFound() {
        PlayerSeasonStatsQueryService queryService = mock(PlayerSeasonStatsQueryService.class);
        when(queryService.isApiEnabled()).thenReturn(true);
        PlayerSeasonStatsDto playerDto = new PlayerSeasonStatsDto(
                CAREER_ID, SEASON, "team-A", "player-1", "Player One", "FWD",
                20, 18,  // appearances, starts
                5, 3, 10, 25,  // goals, assists, keyPasses, shots
                1, 0,  // yellowCards, redCards
                1, 3,  // injuries, fouls
                2, 0,  // matchesMissedInjuredApprox, matchesMissedSuspendedApprox
                7.5, 9.0, 5.5,  // averageRating, bestRating, worstRating
                30  // lastUpdatedRound
        );
        when(queryService.getPlayerSeasonStats(eq(CAREER_ID), eq(SEASON), isNull(), eq("player-1"), isNull()))
                .thenReturn(PlayerSeasonStatsResponse.builder()
                        .careerId(CAREER_ID)
                        .season(SEASON)
                        .playerStats(List.of(playerDto))
                        .totalGoals(5)
                        .totalAssists(3)
                        .totalAppearances(20)
                        .averageRating(7.5)
                        .incomplete(false)
                        .message("ok")
                        .build());

        PlayerSeasonStatsController controller = new PlayerSeasonStatsController(queryService);
        Mono<ResponseEntity<Object>> result = controller.getPlayerStats(CAREER_ID, SEASON, "player-1");

        ResponseEntity<Object> entity = result.block();
        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlayerSeasonStatsResponse body = (PlayerSeasonStatsResponse) entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.playerStats()).hasSize(1);
        assertThat(body.playerStats().get(0).playerId()).isEqualTo("player-1");
    }

    @Test
    void getSinglePlayerStats_returns404WhenPlayerMissing() {
        PlayerSeasonStatsQueryService queryService = mock(PlayerSeasonStatsQueryService.class);
        when(queryService.isApiEnabled()).thenReturn(true);
        // Aggregator returns empty list when no data for the requested playerId
        when(queryService.getPlayerSeasonStats(eq(CAREER_ID), eq(SEASON), isNull(), eq("missing-player"), isNull()))
                .thenReturn(PlayerSeasonStatsResponse.builder()
                        .careerId(CAREER_ID)
                        .season(SEASON)
                        .playerStats(List.of())
                        .totalGoals(0)
                        .totalAssists(0)
                        .totalAppearances(0)
                        .averageRating(0.0)
                        .incomplete(false)
                        .message("ok")
                        .build());

        PlayerSeasonStatsController controller = new PlayerSeasonStatsController(queryService);
        Mono<ResponseEntity<Object>> result = controller.getPlayerStats(CAREER_ID, SEASON, "missing-player");

        ResponseEntity<Object> entity = result.block();
        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void featureDisabled_returns404() {
        PlayerSeasonStatsQueryService queryService = mock(PlayerSeasonStatsQueryService.class);
        when(queryService.isApiEnabled()).thenReturn(false);

        PlayerSeasonStatsController controller = new PlayerSeasonStatsController(queryService);

        // All three endpoints return 404 when feature disabled
        ResponseEntity<Object> allStats = controller.getPlayerSeasonStats(CAREER_ID, SEASON).block();
        ResponseEntity<Object> teamStats = controller.getTeamPlayerSeasonStats(CAREER_ID, SEASON, "team-A").block();
        ResponseEntity<Object> playerStats = controller.getPlayerStats(CAREER_ID, SEASON, "player-1").block();

        assertThat(allStats).isNotNull();
        assertThat(allStats.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(teamStats).isNotNull();
        assertThat(teamStats.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(playerStats).isNotNull();
        assertThat(playerStats.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPlayerSeasonStats_nullCareerId_returns400() {
        PlayerSeasonStatsQueryService queryService = mock(PlayerSeasonStatsQueryService.class);
        when(queryService.isApiEnabled()).thenReturn(true);

        PlayerSeasonStatsController controller = new PlayerSeasonStatsController(queryService);
        Mono<ResponseEntity<Object>> result = controller.getPlayerSeasonStats(null, SEASON);

        ResponseEntity<Object> entity = result.block();
        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getPlayerSeasonStats_blankCareerId_returns400() {
        PlayerSeasonStatsQueryService queryService = mock(PlayerSeasonStatsQueryService.class);
        when(queryService.isApiEnabled()).thenReturn(true);

        PlayerSeasonStatsController controller = new PlayerSeasonStatsController(queryService);
        Mono<ResponseEntity<Object>> result = controller.getPlayerSeasonStats("  ", SEASON);

        ResponseEntity<Object> entity = result.block();
        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getPlayerSeasonStats_nullSeason_returns400() {
        PlayerSeasonStatsQueryService queryService = mock(PlayerSeasonStatsQueryService.class);
        when(queryService.isApiEnabled()).thenReturn(true);

        PlayerSeasonStatsController controller = new PlayerSeasonStatsController(queryService);
        Mono<ResponseEntity<Object>> result = controller.getPlayerSeasonStats(CAREER_ID, null);

        ResponseEntity<Object> entity = result.block();
        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}