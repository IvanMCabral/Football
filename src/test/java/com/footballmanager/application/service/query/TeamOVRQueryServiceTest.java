package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.view.WorldView;
import com.footballmanager.application.service.world.WorldQueryService;
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
        TeamOVRQueryService service = new TeamOVRQueryService(worldQueryService);
        UUID leagueId = UUID.randomUUID();
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
        when(worldQueryService.worldViewForQuery(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .thenReturn(Mono.just(new WorldView(UUID.randomUUID(), List.of(), List.of(team),
                        List.of(player), Map.of())));

        var result = service.buildTeamsWithOVR(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), leagueId).block();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ovr()).isEqualTo(80);
        verify(worldQueryService, times(1)).worldViewForQuery(any());
    }
}
