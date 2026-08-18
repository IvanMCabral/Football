package com.footballmanager.application.service.world;

import com.footballmanager.application.service.world.load.*;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.application.observability.ReloadWorldTiming;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Orchestrator para carga de datos base.
 * Delega a servicios especializados:
 * - LeagueTeamSyncService: sincronizacion league-team
 * - LeagueLoaderService: carga de leagues
 * - TeamPlayerLoaderService: carga de teams y players
 */
@Service
@RequiredArgsConstructor
public class LoadBaseDataService {

    private final LeagueTeamSyncService leagueTeamSyncService;
    private final LeagueLoaderService leagueLoaderService;
    private final TeamPlayerLoaderService teamPlayerLoaderService;

    /**
     * The canonical catalog is shared by every new manager.  In production it
     * is therefore safe to reuse for a short window instead of loading the
     * complete roster from PostgreSQL for every catalog query.  Tests keep the
     * default zero duration so they retain their isolation.
     */
    @Value("${app.world.canonical-cache-ttl:0s}")
    private Duration canonicalCacheTtl = Duration.ZERO;

    private volatile Mono<BaseDataResult> cachedCanonical;

    /**
     * Carga todos los datos base para un usuario.
     */
    public Mono<BaseDataResult> load(UUID userId) {
        return leagueTeamSyncService.loadLeagueTeamsMap(userId)
                .flatMap(leagueTeamsMap -> Mono.zip(
                        Mono.just(leagueTeamsMap),
                        leagueLoaderService.loadLeagues(userId),
                        teamPlayerLoaderService.loadTeamsAndPlayers(userId, leagueTeamsMap)
                ))
                .flatMap(tuple -> {
                    Map<UUID, UUID> map = tuple.getT1();
                    List<WorldLeague> leagues = tuple.getT2();
                    TeamPlayerLoaderService.TeamsAndPlayersResult teamsAndPlayers = tuple.getT3();

                    return Mono.just(new BaseDataResult(
                            leagues,
                            teamsAndPlayers.teams(),
                            teamsAndPlayers.players(),
                            map
                    ));
                });
    }

    public Mono<BaseDataResult> load(UUID userId, ReloadWorldTiming timing) {
        return Mono.defer(() -> {
            Mono<Map<UUID, UUID>> leagueTeams = timing.measure(
                    "leagueTeamSyncMs", leagueTeamSyncService.loadLeagueTeamsMap(userId, timing));
            Mono<List<WorldLeague>> leagues = timing.measure(
                    "leagueLoadMs", leagueLoaderService.loadLeagues(userId, timing));
            return leagueTeams.flatMap(leagueTeamsMap ->
                    Mono.zip(
                            Mono.just(leagueTeamsMap),
                            leagues,
                            timing.measure("teamPlayerLoadMs",
                                    teamPlayerLoaderService.loadTeamsAndPlayers(userId, leagueTeamsMap, timing)))
                            .map(tuple -> new BaseDataResult(
                                    tuple.getT2(),
                                    tuple.getT3().teams(),
                                    tuple.getT3().players(),
                                    tuple.getT1())));
        }).transform(publisher -> timing.measure("canonicalLoadMs", publisher));
    }

    /**
     * Rebuilds the shared canonical catalog from durable sources only. This path
     * never reads or initializes an owner's Redis relation cache.
     */
    public Mono<BaseDataResult> loadCanonical(UUID ownerId) {
        if (canonicalCacheTtl.isZero() || canonicalCacheTtl.isNegative()) {
            return loadCanonicalFromSources(ownerId);
        }
        Mono<BaseDataResult> cached = cachedCanonical;
        if (cached != null) {
            return cached;
        }
        Mono<BaseDataResult> loaded = loadCanonicalFromSources(ownerId).cache(canonicalCacheTtl);
        cachedCanonical = loaded;
        return loaded;
    }

    /** Loads the canonical team catalog without hydrating players or traits. */
    public Mono<List<WorldTeam>> loadCanonicalTeams() {
        return leagueTeamSyncService.loadCanonicalLeagueTeamsMap()
                .flatMap(teamPlayerLoaderService::loadTeamsOnly);
    }

    private Mono<BaseDataResult> loadCanonicalFromSources(UUID ownerId) {
        return leagueTeamSyncService.loadCanonicalLeagueTeamsMap()
                .flatMap(leagueTeamsMap -> Mono.zip(
                        leagueLoaderService.loadCanonicalLeagues(),
                        teamPlayerLoaderService.loadTeamsAndPlayers(ownerId, leagueTeamsMap))
                        .map(tuple -> new BaseDataResult(
                                tuple.getT1(),
                                tuple.getT2().teams(),
                                tuple.getT2().players(),
                                leagueTeamsMap)));
    }

    public record BaseDataResult(
            List<WorldLeague> leagues,
            List<WorldTeam> teams,
            List<WorldPlayer> players,
            Map<UUID, UUID> leagueTeamsMap
    ) {}
}
