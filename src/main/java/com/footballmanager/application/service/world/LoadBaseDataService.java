package com.footballmanager.application.service.world;

import com.footballmanager.application.service.world.load.*;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.application.observability.ReloadWorldTiming;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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
    private volatile Mono<CanonicalBaseData> canonicalBaseDataCache;

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
            return leagueTeams.flatMap(leagueTeamsMap ->
                    timing.measure("canonicalLoadMs", canonicalBaseData(userId, leagueTeamsMap, timing))
                            .map(data -> new BaseDataResult(data.leagues(), data.teams(), data.players(), leagueTeamsMap)));
        });
    }

    private Mono<CanonicalBaseData> canonicalBaseData(UUID userId,
                                                       Map<UUID, UUID> leagueTeamsMap,
                                                       ReloadWorldTiming timing) {
        Mono<CanonicalBaseData> cached = canonicalBaseDataCache;
        if (cached != null) return cached;
        Mono<CanonicalBaseData> created = Mono.zip(
                        leagueLoaderService.loadLeagues(userId, timing),
                        teamPlayerLoaderService.loadTeamsAndPlayers(userId, leagueTeamsMap, timing))
                .map(tuple -> new CanonicalBaseData(
                        tuple.getT1(), tuple.getT2().teams(), tuple.getT2().players()))
                .cache(java.time.Duration.ofMinutes(5));
        canonicalBaseDataCache = created;
        return created;
    }

    private record CanonicalBaseData(List<WorldLeague> leagues,
                                     List<com.footballmanager.domain.model.entity.WorldTeam> teams,
                                     List<com.footballmanager.domain.model.entity.WorldPlayer> players) {}

    public record BaseDataResult(
            List<WorldLeague> leagues,
            List<WorldTeam> teams,
            List<WorldPlayer> players,
            Map<UUID, UUID> leagueTeamsMap
    ) {}
}
