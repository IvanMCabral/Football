package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.ports.in.query.BuildWorldViewUseCase;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorldQueryServiceTeamsCatalogTest {

    @Test
    void leaguesForNewOwnerReadOnlyLeagueMetadata() {
        var builder = mock(BuildWorldViewUseCase.class);
        var snapshots = mock(WorldSnapshotRepository.class);
        var loader = mock(LoadBaseDataService.class);
        var owner = UUID.randomUUID();
        var league = mock(com.footballmanager.domain.model.entity.WorldLeague.class);
        when(snapshots.findByUserId(owner)).thenReturn(Mono.empty());
        when(loader.loadCanonicalLeagues()).thenReturn(Mono.just(List.of(league)));
        assertThat(new WorldQueryService(builder, snapshots, loader).getLeagues(owner).block())
                .containsExactly(league);
        verify(loader).loadCanonicalLeagues();
        verify(loader, never()).loadCanonical(any());
        verifyNoInteractions(builder);
    }

    @Test
    void leaguesPreserveOwnerCustomMetadataIncludingAnEmptyList() {
        var builder = mock(BuildWorldViewUseCase.class);
        var snapshots = mock(WorldSnapshotRepository.class);
        var loader = mock(LoadBaseDataService.class);
        var owner = UUID.randomUUID();
        var snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        snapshot.addWorldTeam(mock(WorldTeam.class));
        var custom = mock(com.footballmanager.domain.model.entity.WorldLeague.class);
        snapshot.setLeagues(List.of(custom));
        when(snapshots.findByUserId(owner)).thenReturn(Mono.just(snapshot));
        var service = new WorldQueryService(builder, snapshots, loader);
        assertThat(service.getLeagues(owner).block()).containsExactly(custom);
        snapshot.setLeagues(List.of());
        assertThat(service.getLeagues(owner).block()).isEmpty();
        verifyNoInteractions(builder, loader);
    }

    @Test
    void leaguesDoNotMaskStorageFailuresAsCanonicalData() {
        var snapshots = mock(WorldSnapshotRepository.class);
        var loader = mock(LoadBaseDataService.class);
        var owner = UUID.randomUUID();
        when(snapshots.findByUserId(owner)).thenReturn(Mono.error(new IllegalStateException("synthetic")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new WorldQueryService(mock(BuildWorldViewUseCase.class), snapshots, loader)
                        .getLeagues(owner).block()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(loader);
    }

    @Test
    void teamsCatalogFallsBackToCanonicalProjectionWithoutBuildingWorldView() {
        BuildWorldViewUseCase buildWorldViewUseCase = mock(BuildWorldViewUseCase.class);
        WorldSnapshotRepository snapshotRepository = mock(WorldSnapshotRepository.class);
        LoadBaseDataService loadBaseDataService = mock(LoadBaseDataService.class);
        WorldTeam team = mock(WorldTeam.class);
        UUID userId = UUID.randomUUID();
        when(snapshotRepository.findByUserId(userId)).thenReturn(Mono.empty());
        when(loadBaseDataService.loadCanonicalTeams()).thenReturn(Mono.just(List.of(team)));

        WorldQueryService service = new WorldQueryService(
                buildWorldViewUseCase, snapshotRepository, loadBaseDataService);

        assertThat(service.getAllTeams(userId).block()).containsExactly(team);
        verify(loadBaseDataService).loadCanonicalTeams();
        verify(snapshotRepository).findByUserId(userId);
        verifyNoInteractions(buildWorldViewUseCase);
    }

    @Test
    void teamsByLeagueFiltersTheCanonicalProjectionWithoutPlayerData() {
        BuildWorldViewUseCase buildWorldViewUseCase = mock(BuildWorldViewUseCase.class);
        WorldSnapshotRepository snapshotRepository = mock(WorldSnapshotRepository.class);
        LoadBaseDataService loadBaseDataService = mock(LoadBaseDataService.class);
        UUID requestedLeague = UUID.randomUUID();
        WorldTeam matching = mock(WorldTeam.class);
        WorldTeam other = mock(WorldTeam.class);
        when(matching.getRealLeagueId()).thenReturn(requestedLeague);
        when(other.getRealLeagueId()).thenReturn(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        when(snapshotRepository.findByUserId(userId)).thenReturn(Mono.empty());
        when(loadBaseDataService.loadCanonicalTeams()).thenReturn(
                Mono.just(List.of(matching, other)));

        WorldQueryService service = new WorldQueryService(
                buildWorldViewUseCase, snapshotRepository, loadBaseDataService);

        assertThat(service.getTeamsByLeague(userId, requestedLeague).block())
                .containsExactly(matching);
        verify(snapshotRepository).findByUserId(userId);
        verifyNoInteractions(buildWorldViewUseCase);
    }

    @Test
    void existingOwnerSnapshotKeepsCustomTeamSemanticsWithoutCanonicalFallback() {
        BuildWorldViewUseCase buildWorldViewUseCase = mock(BuildWorldViewUseCase.class);
        WorldSnapshotRepository snapshotRepository = mock(WorldSnapshotRepository.class);
        LoadBaseDataService loadBaseDataService = mock(LoadBaseDataService.class);
        UUID userId = UUID.randomUUID();
        WorldTeam customTeam = mock(WorldTeam.class);
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(userId);
        snapshot.addWorldTeam(customTeam);
        when(snapshotRepository.findByUserId(userId)).thenReturn(Mono.just(snapshot));

        WorldQueryService service = new WorldQueryService(
                buildWorldViewUseCase, snapshotRepository, loadBaseDataService);

        assertThat(service.getAllTeams(userId).block()).containsExactly(customTeam);
        verify(snapshotRepository).findByUserId(userId);
        verifyNoInteractions(buildWorldViewUseCase, loadBaseDataService);
    }
}
