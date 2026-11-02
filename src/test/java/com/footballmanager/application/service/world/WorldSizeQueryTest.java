package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.valueobject.WorldSize;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldSizeQueryTest {
    @Test void completeSizeDoesNotMaterializeOrRecreateTheWorld() {
        var repository = mock(WorldSnapshotRepository.class);
        var creator = mock(WorldSnapshotCreator.class);
        UUID owner = UUID.randomUUID();
        WorldSize expected = new WorldSize(70, 1680, 4);
        when(repository.findSizeByUserId(owner)).thenReturn(Mono.just(expected));
        assertEquals(expected, new WorldSnapshotService(repository, creator).getSize(owner).block());
        verify(repository, never()).findByUserId(any());
        verifyNoInteractions(creator);
    }

    @Test void absentAndIncompleteWorldsKeepTheExistingCreationBehavior() {
        for (Mono<WorldSize> stored : java.util.List.of(Mono.<WorldSize>empty(), Mono.just(new WorldSize(1, 0, 1)))) {
            var repository = mock(WorldSnapshotRepository.class);
            var creator = mock(WorldSnapshotCreator.class);
            UUID owner = UUID.randomUUID();
            WorldSnapshot created = new WorldSnapshot();
            when(repository.findSizeByUserId(owner)).thenReturn(stored);
            when(creator.create(owner)).thenReturn(Mono.just(created));
            assertEquals(WorldSize.from(created), new WorldSnapshotService(repository, creator).getSize(owner).block());
            verify(creator).create(owner);
        }
    }
}
