package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.view.WorldView;
import com.footballmanager.domain.model.view.WorldPlayerOvrProjection;
import com.footballmanager.application.service.world.WorldQueryService;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TeamOVRQueryServiceTest {

    @Test
    void buildsLeagueOvrProjectionFromOneWorldViewRead() {
        WorldQueryService worldQueryService = mock(WorldQueryService.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        WorldSnapshotRepository snapshotRepository = mock(WorldSnapshotRepository.class);
        TeamOVRQueryService service = new TeamOVRQueryService(
                worldQueryService, playerRepository, snapshotRepository);
        UUID leagueId = UUID.randomUUID();
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        WorldTeam team = mock(WorldTeam.class);
        WorldPlayer player = mock(WorldPlayer.class);
        when(team.getRealLeagueId()).thenReturn(leagueId);
        when(team.getWorldTeamId()).thenReturn("team-1");
        when(team.getName()).thenReturn("Team");
        when(team.getCountry()).thenReturn("ES");
        when(team.getBaseFormation()).thenReturn("4-4-2");
        when(team.getBaseBudget()).thenReturn(java.math.BigDecimal.ONE);
        when(player.getWorldTeamId()).thenReturn("team-1");
        when(player.calculateOverall()).thenReturn(80);
        when(snapshotRepository.existsByUserId(userId)).thenReturn(Mono.just(true));
        when(worldQueryService.worldViewForQuery(userId))
                .thenReturn(Mono.just(new WorldView(UUID.randomUUID(), List.of(), List.of(team),
                        List.of(player), Map.of())));

        var result = service.buildTeamsWithOVR(
                userId, leagueId).block();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ovr()).isEqualTo(80);
        verify(worldQueryService, times(1)).worldViewForQuery(any());
    }

    @Test
    void canonicalOwnerWithoutSnapshotUsesRawProjectionAndCanonicalCalculator() {
        WorldQueryService worldQueryService = mock(WorldQueryService.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        WorldSnapshotRepository snapshotRepository = mock(WorldSnapshotRepository.class);
        TeamOVRQueryService service = new TeamOVRQueryService(
                worldQueryService, playerRepository, snapshotRepository);
        UUID leagueId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorldTeam team = mock(WorldTeam.class);
        UUID teamId = UUID.randomUUID();
        when(team.getRealLeagueId()).thenReturn(leagueId);
        when(team.getRealTeamId()).thenReturn(teamId);
        when(team.getWorldTeamId()).thenReturn(teamId.toString());
        when(team.getName()).thenReturn("Team");
        when(team.getCountry()).thenReturn("ES");
        when(team.getBaseFormation()).thenReturn("4-4-2");
        when(team.getBaseBudget()).thenReturn(java.math.BigDecimal.ONE);
        when(snapshotRepository.existsByUserId(userId)).thenReturn(Mono.just(false));
        when(worldQueryService.getCanonicalTeamsByLeague(leagueId)).thenReturn(Mono.just(List.of(team)));
        when(playerRepository.findPlayersForOvrFromDatabase()).thenReturn(Mono.just(List.of(
                new WorldPlayerOvrProjection(teamId, "MID", 81, 81, 81, 81, 81, 81))));

        var result = service.buildTeamsWithOVR(userId, leagueId).block();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ovr()).isEqualTo(81);
        assertThat(result.get(0).playerCount()).isEqualTo(1);
        verify(playerRepository).findPlayersForOvrFromDatabase();
        verify(worldQueryService, never()).worldViewForQuery(any());
    }

    @Test
    void canonicalProjectionPreservesTheIndependentReviewStCounterexample() {
        WorldQueryService worldQueryService = mock(WorldQueryService.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        WorldSnapshotRepository snapshotRepository = mock(WorldSnapshotRepository.class);
        TeamOVRQueryService service = new TeamOVRQueryService(
                worldQueryService, playerRepository, snapshotRepository);
        UUID leagueId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        WorldTeam team = WorldTeam.fromRealTeam(teamId, leagueId, "ST team", "ES", "Madrid",
                java.math.BigDecimal.ONE, "4-3-3");
        WorldPlayer canonical = WorldPlayer.fromCanonicalPlayer(
                userId, UUID.randomUUID(), team.getWorldTeamId(), "ST player", 22, "ST",
                90, 50, 50, 50, 50, 50, java.math.BigDecimal.ONE);
        WorldPlayerOvrProjection projection = new WorldPlayerOvrProjection(
                teamId, "ST", 90, 50, 50, 50, 50, 50);

        assertThat(canonical.calculateOverall()).isEqualTo(57);
        when(snapshotRepository.existsByUserId(userId)).thenReturn(Mono.just(false));
        when(worldQueryService.getCanonicalTeamsByLeague(leagueId)).thenReturn(Mono.just(List.of(team)));
        when(playerRepository.findPlayersForOvrFromDatabase()).thenReturn(Mono.just(List.of(projection)));

        var result = service.buildTeamsWithOVR(userId, leagueId).block();

        assertThat(projection.calculateOverall()).isEqualTo(57);
        assertThat(result).extracting(TeamOvrView::id).containsExactly(team.getWorldTeamId());
        assertThat(result.get(0).ovr()).isEqualTo(canonical.calculateOverall());
    }
}
