package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.ports.out.world.CanonicalWorldCatalogSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Canonical catalog recovery backed by the same PostgreSQL/loaders used for world creation. */
@Service
public final class DurableCanonicalWorldCatalogSource implements CanonicalWorldCatalogSource {

    private final LoadBaseDataService loadBaseDataService;

    public DurableCanonicalWorldCatalogSource(LoadBaseDataService loadBaseDataService) {
        this.loadBaseDataService = loadBaseDataService;
    }

    @Override
    public Mono<WorldSnapshot> rebuild(UUID ownerId) {
        return loadBaseDataService.loadCanonical(ownerId).map(base -> {
            WorldSnapshot catalog = new WorldSnapshot();
            catalog.setUserId(ownerId);
            catalog.setCreatedAt(Instant.EPOCH);
            catalog.setLastUpdated(Instant.EPOCH);
            catalog.setLeagues(base.leagues());
            LinkedHashMap<String, com.footballmanager.domain.model.entity.WorldTeam> teams = new LinkedHashMap<>();
            base.teams().forEach(team -> teams.put(team.getWorldTeamId(), team));
            catalog.setWorldTeams(teams);
            LinkedHashMap<String, com.footballmanager.domain.model.entity.WorldPlayer> players = new LinkedHashMap<>();
            base.players().forEach(player -> players.put(player.getWorldPlayerId(), player));
            catalog.setWorldPlayers(players);
            DivisionRankDistributor.applyPerLeagueRankDivision(catalog);
            return catalog;
        });
    }
}
