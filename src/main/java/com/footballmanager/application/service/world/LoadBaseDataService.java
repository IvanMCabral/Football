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
