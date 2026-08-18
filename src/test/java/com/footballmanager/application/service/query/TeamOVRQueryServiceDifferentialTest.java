package com.footballmanager.application.service.query;

import com.footballmanager.application.service.world.WorldQueryService;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.view.WorldPlayerOvrProjection;
import com.footballmanager.domain.model.view.WorldView;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamOVRQueryServiceDifferentialTest {

    @Test
    void optimizedTeamsWithOvrMatchesReferenceAcrossRostersAndLeagues() {
        WorldQueryService worldQueryService = mock(WorldQueryService.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        WorldSnapshotRepository snapshotRepository = mock(WorldSnapshotRepository.class);
        TeamOVRQueryService service = new TeamOVRQueryService(
                worldQueryService, playerRepository, snapshotRepository);
        UUID userId = UUID.randomUUID();

        for (RosterScenario scenario : List.of(
                scenario(UUID.randomUUID(), new int[]{24, 6, 3, 1, 0, 2}),
                scenario(UUID.randomUUID(), new int[]{24, 1}))) {
            when(snapshotRepository.existsByUserId(userId)).thenReturn(Mono.just(false));
            when(worldQueryService.getCanonicalTeamsByLeague(scenario.leagueId()))
                    .thenReturn(Mono.just(scenario.teams()));
            when(playerRepository.findPlayersForOvrFromDatabase())
                    .thenReturn(Mono.just(scenario.projections()));
            when(worldQueryService.worldViewForQuery(userId))
                    .thenReturn(Mono.just(new WorldView(userId, List.of(), scenario.teams(),
                            scenario.worldPlayers(), java.util.Map.of())));

            List<TeamOvrView> optimized = service.buildTeamsWithOVR(userId, scenario.leagueId()).block();
            List<TeamOvrView> reference = service.buildTeamsWithOVR(userId, scenario.teams()).block();

            assertThat(optimized)
                    .as("optimized/reference response for roster sizes %s", scenario.rosterSizes())
                    .containsExactlyElementsOf(reference);
        }
    }

    private RosterScenario scenario(UUID leagueId, int[] rosterSizes) {
        List<WorldTeam> teams = new ArrayList<>();
        List<WorldPlayerOvrProjection> projections = new ArrayList<>();
        List<WorldPlayer> worldPlayers = new ArrayList<>();
        String[] positions = {"GK", "CB", "CM", "LW", "ST"};

        for (int teamIndex = 0; teamIndex < rosterSizes.length; teamIndex++) {
            UUID teamId = UUID.randomUUID();
            WorldTeam team = WorldTeam.fromRealTeam(teamId, leagueId, "Team " + teamIndex,
                    "ES", "City", BigDecimal.valueOf(100 + teamIndex), "4-3-3");
            teams.add(team);
            for (int playerIndex = 0; playerIndex < rosterSizes[teamIndex]; playerIndex++) {
                int attack = 35 + ((teamIndex * 17 + playerIndex * 11) % 65);
                int defense = 30 + ((teamIndex * 13 + playerIndex * 7) % 70);
                int technique = 40 + ((teamIndex * 19 + playerIndex * 5) % 60);
                int speed = 25 + ((teamIndex * 23 + playerIndex * 3) % 75);
                int stamina = 20 + ((teamIndex * 29 + playerIndex * 13) % 80);
                int mentality = 45 + ((teamIndex * 31 + playerIndex * 17) % 55);
                String position = positions[(teamIndex + playerIndex) % positions.length];
                WorldPlayerOvrProjection projection = new WorldPlayerOvrProjection(
                        teamId, position, attack, defense, technique, speed, stamina, mentality);
                projections.add(projection);
                worldPlayers.add(WorldPlayer.fromCanonicalPlayer(
                        UUID.randomUUID(), UUID.randomUUID(), team.getWorldTeamId(),
                        "Player " + teamIndex + "-" + playerIndex, 22, position,
                        attack, defense, technique, speed, stamina, mentality, BigDecimal.ONE));
            }
        }
        return new RosterScenario(leagueId, teams, projections, worldPlayers, rosterSizes);
    }

    private record RosterScenario(
            UUID leagueId,
            List<WorldTeam> teams,
            List<WorldPlayerOvrProjection> projections,
            List<WorldPlayer> worldPlayers,
            int[] rosterSizes
    ) {
    }
}
