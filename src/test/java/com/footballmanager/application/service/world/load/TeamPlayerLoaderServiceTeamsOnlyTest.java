package com.footballmanager.application.service.world.load;

import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TeamPlayerLoaderServiceTeamsOnlyTest {

    @Test
    void teamsOnlyLoadsOneTeamQueryAndDoesNotTouchPlayerRepository() {
        TeamRepository teamRepository = mock(TeamRepository.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        TeamPlayerLoaderService service = new TeamPlayerLoaderService(teamRepository, playerRepository);
        UUID teamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Team team = mock(Team.class);
        when(team.getId()).thenReturn(TeamId.of(teamId));
        when(team.getName()).thenReturn("Team One");
        when(team.getCountry()).thenReturn("ES");
        when(team.getBudget()).thenReturn(java.math.BigDecimal.ONE);
        when(team.getFormation()).thenReturn(null);
        when(team.getDivision()).thenReturn(null);
        when(teamRepository.findAllFromDatabase()).thenReturn(Flux.just(team));

        var result = service.loadTeamsOnly(Map.of(teamId, leagueId)).block();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRealLeagueId()).isEqualTo(leagueId);
        verify(teamRepository).findAllFromDatabase();
        verifyNoInteractions(playerRepository);
    }
}
