package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.ports.out.world.CanonicalWorldCatalogSource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Canonical catalog recovery backed by the same PostgreSQL/loaders used for world creation. */
@Service
public final class DurableCanonicalWorldCatalogSource implements CanonicalWorldCatalogSource {

    private final LoadBaseDataService loadBaseDataService;
    private final java.time.Duration cacheTtl;
    private volatile Mono<WorldSnapshot> cachedCatalog;

    public DurableCanonicalWorldCatalogSource(LoadBaseDataService loadBaseDataService) {
        this(loadBaseDataService, java.time.Duration.ZERO);
    }

    @Autowired
    public DurableCanonicalWorldCatalogSource(
            LoadBaseDataService loadBaseDataService,
            @Value("${app.world.canonical-cache-ttl:0s}") java.time.Duration cacheTtl) {
        this.loadBaseDataService = loadBaseDataService;
        this.cacheTtl = cacheTtl;
    }

    @Override
    public Mono<WorldSnapshot> rebuild(UUID ownerId) {
        Mono<WorldSnapshot> catalog = cachedCatalog;
        if (catalog == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            catalog = loadBaseDataService.loadCanonical(ownerId).map(base -> {
            WorldSnapshot snapshot = new WorldSnapshot();
            snapshot.setUserId(ownerId);
            snapshot.setCreatedAt(Instant.EPOCH);
            snapshot.setLastUpdated(Instant.EPOCH);
            snapshot.setLeagues(base.leagues());
            LinkedHashMap<String, com.footballmanager.domain.model.entity.WorldTeam> teams = new LinkedHashMap<>();
            base.teams().forEach(team -> teams.put(team.getWorldTeamId(), team));
            snapshot.setWorldTeams(teams);
            LinkedHashMap<String, com.footballmanager.domain.model.entity.WorldPlayer> players = new LinkedHashMap<>();
            base.players().forEach(player -> players.put(player.getWorldPlayerId(), player));
            snapshot.setWorldPlayers(players);
            DivisionRankDistributor.applyPerLeagueRankDivision(snapshot);
            return snapshot;
            });
            if (!cacheTtl.isZero() && !cacheTtl.isNegative()) {
                catalog = catalog.cache(cacheTtl);
                cachedCatalog = catalog;
            }
        }
        return catalog;
    }
}
