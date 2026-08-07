package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.out.league.LeagueTeamRepository;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LeagueTeamCommandServiceTest {

    @Test
    void worldPersistenceFailureIsNotReportedAsSuccessfulCommand() {
        LeagueTeamRepository links = mock(LeagueTeamRepository.class);
        WorldSnapshotRepository worlds = mock(WorldSnapshotRepository.class);
        LeagueTeamCommandService service = new LeagueTeamCommandService(links, worlds);
        UUID owner = UUID.randomUUID();
        UUID league = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        WorldTeam team = new WorldTeam();
        team.setWorldTeamId(teamId.toString());
        snapshot.addWorldTeam(team);
        when(links.addTeamToLeague(owner, league, teamId)).thenReturn(Mono.empty());
        when(worlds.findByUserId(owner)).thenReturn(Mono.just(snapshot));
        when(worlds.save(snapshot)).thenReturn(Mono.error(new IllegalStateException("lifecycle context required")));

        assertThrows(RuntimeException.class,
                () -> service.addTeamToLeague(owner, league, teamId).block());
        verify(worlds).save(snapshot);
    }
}
