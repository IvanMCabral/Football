package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DurableCanonicalWorldCatalogSourceTest {

    @Test
    void rebuildUsesDurableCanonicalPathAndNeverOwnerCachePath() {
        UUID owner = UUID.randomUUID();
        LoadBaseDataService loader = mock(LoadBaseDataService.class);
        when(loader.loadCanonical(owner)).thenReturn(Mono.just(new LoadBaseDataService.BaseDataResult(
                List.<WorldLeague>of(), List.<WorldTeam>of(), List.<WorldPlayer>of(), Map.of())));

        var source = new DurableCanonicalWorldCatalogSource(loader);
        var catalog = source.rebuild(owner).block();

        assertEquals(owner, catalog.getUserId());
        verify(loader).loadCanonical(owner);
        verify(loader, never()).load(owner);
    }
}
